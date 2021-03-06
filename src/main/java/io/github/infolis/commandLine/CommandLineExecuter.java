package io.github.infolis.commandLine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.BadRequestException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.infolis.algorithm.Algorithm;
import io.github.infolis.algorithm.ComplexAlgorithm;
import io.github.infolis.algorithm.Indexer;
import io.github.infolis.algorithm.SearchResultLinker;
import io.github.infolis.algorithm.LuceneSearcher;
import io.github.infolis.algorithm.TextAndMetaDataExtractor;
import io.github.infolis.algorithm.TextExtractor;
import io.github.infolis.datastore.DataStoreClient;
import io.github.infolis.datastore.DataStoreClientFactory;
import io.github.infolis.datastore.DataStoreStrategy;
import io.github.infolis.datastore.FileResolver;
import io.github.infolis.datastore.FileResolverFactory;
import io.github.infolis.model.BootstrapStrategy;
import io.github.infolis.model.EntityType;
import io.github.infolis.model.Execution;
import io.github.infolis.model.entity.Entity;
import io.github.infolis.model.entity.InfolisFile;
import io.github.infolis.model.entity.InfolisPattern;
import io.github.infolis.infolink.querying.QueryService;
import io.github.infolis.util.RegexUtils;
import io.github.infolis.util.SerializationUtils;
import org.kohsuke.args4j.CmdLineException;

/**
 * CLI to Infolis to make it easy to run an execution and store its results in a
 * JSON file.
 *
 */
public class CommandLineExecuter {

    private static final Logger log = LoggerFactory.getLogger(CommandLineExecuter.class);

    private DataStoreClient dataStoreClient = DataStoreClientFactory.create(DataStoreStrategy.TEMPORARY);
    private FileResolver fileResolver = FileResolverFactory.create(DataStoreStrategy.LOCAL);

    @Option(name = "--text-dir", usage = "Directory containing *.txt", metaVar = "TEXTDIR")
    private Path textDir;

    @Option(name = "--pdf-dir", usage = "Directory containing *.pdf", metaVar = "PDFDIR")
    private Path pdfDir;

    @Option(name = "--db-dir", usage = "Directory to hold JSON database dump", metaVar = "OUTPUTDIR", required = true)
    private Path dbDir;

    @Option(name = "--index-dir", usage = "Directory to contain the Lucene index (no index unless specified)", metaVar = "INDEXDIR", depends = {"--tag"})
    private Path indexDir;

    @Option(name = "--meta-dir", usage = "Directory to contain the metadata files", metaVar = "METADIR", depends = {"--pdf-dir"})
    private Path metaDir;

    @Option(name = "--json", usage = "Execution as JSON", metaVar = "JSON")
    private Path json;

    //TODO: support multiple tags (e.g. domain, journal, language)
    @Option(name = "--tag", usage = "tag, also JSON dump basename", metaVar = "TAG", required = true)
    private String tag;

    @Option(name = "--log-level", usage = "minimum log level")
    private String logLevel = "DEBUG";

    @Option(name = "--convert-to-text", usage = "whether to convert to text before execution", depends = {"--pdf-dir"})
    private boolean shouldConvertToText = false;

    @Option(name = "--search-candidates", usage = "look for files that match a set of queries", depends = {"--queries-file", "--tag"})
    private boolean searchCandidatesMode = false;

    @Option(name = "--queries-file", usage = "csv-file containing one query term per line", metaVar = "QUERIESFILE", depends = {"--search-candidates"})
    private String queriesFile;

    // This is set so we can accept --convert-to-text without JSON and not try to execute anything
    private boolean convertToTextMode = false;

    @SuppressWarnings("unchecked")
    private void setExecutionFromJSON(JsonObject jsonObject, Execution exec) {
        try {
            // iterate through the entries in the JSON file
            for (Entry<String, JsonValue> values : jsonObject.entrySet()) {
                switch (values.getValue().getValueType()) {
                    case STRING:
                    case NUMBER:
                    case TRUE:
                    case FALSE:
                    // algorithm has to be handled as a special case since we
                        // need
                        // to find the class
                        if (values.getKey().equals("algorithm")) {
                            String algorithmName = values.getValue().toString();
                            algorithmName = algorithmName.replace("\"", "");
                            if (!algorithmName.startsWith("io.github.infolis.algorithm")) {
                                algorithmName = "io.github.infolis.algorithm." + algorithmName;
                            }
                            try {
                                Class<? extends Algorithm> algoClass;
                                algoClass = (Class<? extends Algorithm>) Class.forName(algorithmName);
                                exec.setAlgorithm(algoClass);
                            } catch (ClassNotFoundException | ClassCastException e1) {
                                throwCLI("No such algorithm: " + algorithmName);
                            }
                            break;
                        }
                        // TODO generic solution for enums?
                        if (values.getKey().equals("bootstrapStrategy")) {
                            BootstrapStrategy b = BootstrapStrategy.valueOf(values.getValue().toString().replace("\"", ""));
                            exec.setBootstrapStrategy(b);
                            break;
                        }

                        if (values.getKey().equals("tokenize")) {
                            exec.setTokenize(Boolean.parseBoolean(values.getValue().toString()));
                            break;
                        }

                        if (values.getKey().equals("searchResultLinkerClass")) {
                            String searchResultLinkerClassName = values.getValue().toString().replace("\"", "");
                            String prefix = "io.github.infolis.algorithm.";
                            if (!searchResultLinkerClassName.startsWith(prefix)) {
                                searchResultLinkerClassName = prefix + searchResultLinkerClassName;
                            }
                            try {
                                Class<? extends SearchResultLinker> searchResultLinkerClass = (Class<? extends SearchResultLinker>) Class.forName(searchResultLinkerClassName);
                                exec.setSearchResultLinkerClass(searchResultLinkerClass);
                            } catch (ClassNotFoundException | ClassCastException e1) {
                                throwCLI("No such SearchResultLinker class: " + searchResultLinkerClassName);
                            }
                            break;
                        }

                        // all other fields are just set
                        exec.setProperty(values.getKey(), values.getValue().toString().replace("\"", ""));
                        break;

                    case ARRAY:
                        if (values.getKey().equals("linkedEntities")) {
                            List<String> entityURIs = new ArrayList<>();
                            JsonArray array = (JsonArray) values.getValue();
                            for (int i = 0; i < array.size(); i++) {

                                JsonObject entityObject = (JsonObject) array.getJsonObject(i);
                                String name = "";
                                String number = "";
                                String identifier = "";
                                Set<String> tags = new HashSet<>();
                                if (entityObject.containsKey("name")) {
                                    name = String.valueOf(entityObject.get("name"));
                                    name = name.substring(1, name.length()-1).trim();
                                }
                                if (entityObject.containsKey("number")) {
                                    number = String.valueOf(entityObject.get("number"));
                                }
                                if (entityObject.containsKey("identifier")) {
                                    identifier = String.valueOf(entityObject.get("identifier"));
                                }
                                // TODO set tags
                                // TODO set entityType
                                Entity entity = new Entity();
                                entity.setTags(tags);
                                entity.setName(name);
                                List<String> numInfo = new ArrayList<>();
                                numInfo.add(number);
                                entity.setNumericInfo(numInfo);
                                entity.addIdentifier(identifier);
                                dataStoreClient.post(Entity.class, entity);
                                entityURIs.add(entity.getUri());
                            }
                            exec.setLinkedEntities(entityURIs);
                            break;
                        }

                        if (values.getKey().equals("queryServiceClasses")) {
                            JsonArray array = (JsonArray) values.getValue();
                            for (int i = 0; i < array.size(); i++) {
                                JsonString stringEntry = array.getJsonString(i);

                                String queryServiceName = stringEntry.getString();
                                queryServiceName = queryServiceName.replace("\"", ""); // XXX why?
                                if (!queryServiceName.startsWith("io.github.infolis.infolink.querying.")) {
                                    queryServiceName = "io.github.infolis.infolink.querying." + queryServiceName;
                                }
                                log.debug("queryServiceClass item: " + queryServiceName);
                                try {
                                    Class<? extends QueryService> queryServiceClass;
                                    queryServiceClass = (Class<? extends QueryService>) Class.forName(queryServiceName);
                                    exec.addQueryServiceClasses(queryServiceClass);
                                } catch (ClassNotFoundException | ClassCastException e1) {
                                    throwCLI("No such queryService: " + queryServiceName);
                                }

                            }
                            break;
                        }
                        JsonArray array = (JsonArray) values.getValue();
                        List<String> listEntries = new ArrayList<>();
                        for (int i = 0; i < array.size(); i++) {
                            JsonString stringEntry = array.getJsonString(i);
                            listEntries.add(stringEntry.getString());
                        }
                        
                        if (values.getKey().equals("infolisFileTags") || values.getKey().equals("infolisPatternTags")) {
                        	exec.setProperty(values.getKey(), new HashSet<>(listEntries));
                        } else if (values.getKey().equals("tags")) {
                        	exec.setTags(new HashSet<>(listEntries));
                        } else exec.setProperty(values.getKey(), listEntries);
                       
                        break;
                    case OBJECT:
                    //$FALL-THROUGH$
                    default:
                        throwCLI("Unhandled value type " + values.getValue().getValueType() + " for JSON key "
                                + values.getKey());
                        break;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throwCLI("No such field", e);
        }
    }

    List<String> getQueryTermsFromFile(String filename) throws IOException {
        return FileUtils.readLines(new File(filename), "UTF-8");
    }

    /**
     * Executes the 'candidate search' mode which fires a LuceneSearcher
     * execution for every search term provided./
     *
     * @param exec2
     *
     * @param exec
     * @throws BadRequestException
     * @throws IOException
     */
    private void executeCandidateSearch(Execution parentExec) throws BadRequestException, IOException {
        if (null == queriesFile) {
            throwCLI("Inconsistency: --search-candidates but queries>File is null.");
        }
        if (null == indexDir) {
            throwCLI("Inconsistency: --search-candidates but no --index-dir given.");
        }
        Set<String> allMatchingFiles = new HashSet<>();
        Map<String, List<String>> matchingFilesByQuery = new HashMap<>();
        for (String query : getQueryTermsFromFile(queriesFile)) {
            String normalizedQuery = RegexUtils.normalizeQuery(query.trim(), true);
            matchingFilesByQuery.put(normalizedQuery, new ArrayList<String>());

            Execution exec = new Execution();
            exec.setAlgorithm(LuceneSearcher.class);
            exec.setTags(new HashSet<>(Arrays.asList(tag)));
            exec.setPhraseSlop(0);
            exec.setIndexDirectory(parentExec.getIndexDirectory());
            // normalize to treat phrase query properly
            InfolisPattern p = new InfolisPattern(normalizedQuery);
            dataStoreClient.post(InfolisPattern.class, p);
            exec.setPatterns(Arrays.asList(p.getUri()));
            dataStoreClient.post(Execution.class, exec);
            exec.instantiateAlgorithm(dataStoreClient, fileResolver).run();
            for (InfolisFile f : dataStoreClient.get(InfolisFile.class, exec.getOutputFiles())) {
                String filename = FilenameUtils.getBaseName(f.getFileName());
                allMatchingFiles.add(filename);
                matchingFilesByQuery.get(normalizedQuery).add(filename);
            }
        }
        Path outFile = dbDir.resolve(FilenameUtils.getBaseName(queriesFile));
        try (OutputStream os = Files.newOutputStream(outFile)) {
            IOUtils.write(StringUtils.join(allMatchingFiles, "\n"), os);
        };
        log.warn("ALL MATCHES: {}", allMatchingFiles);
        log.warn("MATCHES BY QUERY: {}", matchingFilesByQuery);
    }
    
    private void extractMetaData(Execution parentExec) {
        Execution extractExec = new Execution();
        extractExec.setAlgorithm(TextAndMetaDataExtractor.class);
        extractExec.setMetaDataFiles(parentExec.getMetaDataFiles());
        extractExec.setInputFiles(parentExec.getInputFiles());
        dataStoreClient.post(Execution.class, extractExec);
        Algorithm algo = extractExec.instantiateAlgorithm(dataStoreClient, fileResolver);
        algo.run();
        log.debug("Extracted metadata for " +parentExec.getMetaDataFiles().size()+" files");
    }

    /**
     * Run the execution
     *
     * @param exec
     * @throws BadRequestException
     * @throws IOException
     */
    private void doExecute(Execution exec) throws BadRequestException, IOException {
        // Set the input files, convert if necessary
        try {
            setExecutionInputFiles(exec);
        } catch (IOException e) {
            throwCLI("Problem setting input files", e);
        }
        // Create index if necessary
        if (indexDir != null) {
            Files.createDirectories(indexDir);
            setExecutionIndexDir(exec);
        }

      try {
            dataStoreClient.post(Execution.class, exec);
            if (convertToTextMode) {
                log.debug("Yay nothing to do. woop dee doo.");
            } else {
                if(metaDir != null) {
                    extractMetaData(exec);
                }
                if (searchCandidatesMode) {
                    executeCandidateSearch(exec);
                } else {
                    exec.instantiateAlgorithm(dataStoreClient, fileResolver).run();
                }
            }
        } catch (BadRequestException e) {
            throwCLI("Execution threw an excepion", e);
        }
        dataStoreClient.dump(dbDir, tag);
    }

    /**
     * Indexes the input files in Lucene and sets the indexDirectory of the
     * execution.
     *
     * @param exec
     */
    private void setExecutionIndexDir(Execution exec) {
        Execution indexerExecution = new Execution();
        indexerExecution.setAlgorithm(Indexer.class);
        indexerExecution.setInputFiles(exec.getInputFiles());
        indexerExecution.setPhraseSlop(0);
        indexerExecution.setOutputDirectory(indexDir.toString());
        dataStoreClient.post(Execution.class, indexerExecution);
        indexerExecution.instantiateAlgorithm(dataStoreClient, fileResolver).run();
        exec.setIndexDirectory(indexerExecution.getOutputDirectory());
    }

    /**
     * Sets the input files for an execution and converts depending on command
     * line arguments.
     *
     * @param exec
     * @throws IOException
     */
    private void setExecutionInputFiles(Execution exec) throws IOException {
        if (null == pdfDir || !Files.exists(pdfDir)) {
            if (shouldConvertToText) {
                throwCLI("Cannot convert to text: Empty/non-existing PDF directory" + pdfDir);
            }
            if (null == textDir || !Files.exists(textDir)) {
                throwCLI("Neither PDFDIR nor TEXTDIR exist");
            } else {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(textDir)) {
                    if (!dirStream.iterator().hasNext()) {
                        dirStream.close();
                        throwCLI("No PDFDIR specified, TEXTDIR exists, but empty.");
                    }
                }
                exec.setInputFiles(postFiles(textDir, "text/plain"));
            }
        } else {            
            if (null == textDir || !Files.exists(textDir)) {
                if (shouldConvertToText) {
                    Files.createDirectories(textDir);
                    if (null == exec.isTokenize()) {
                        log.warn("Warning: tokenize parameter not set. Defaulting to true for text extraction and all algorithms to be applied on extracted texts");
                        exec.setTokenize(true);
                    } 
                    exec.setInputFiles(convertPDF(postFiles(pdfDir, "application/pdf"), exec.getStartPage(), exec.isRemoveBib(), exec.getOverwriteTextfiles(), exec.isTokenize(), exec.getTokenizeNLs(), exec.getPtb3Escaping()));

                } else {
                	// complex algorithm can convert pdfs on demand, others can't and need --convert-to-text option
                	if (ComplexAlgorithm.class.isAssignableFrom(exec.getAlgorithm())) exec.setInputFiles(postFiles(pdfDir, "application/pdf"));
                	else if (TextExtractor.class.equals(exec.getAlgorithm())) exec.setInputFiles(postFiles(pdfDir, "application/pdf"));
                	else throwCLI("PDFDIR specified, TEXTDIR unspecified/empty, but --convert-to-text not set");
                }
            } else {
                if (shouldConvertToText) {
                    //System.err.println("WARNING: Both --text-dir '" + textDir + "' and --pdf-dir '" + pdfDir
                    //        + "' were specified. Will possibly clobber text files in conversion!");
                    System.err.println("WARNING: Both --text-dir '" + textDir + "' and --pdf-dir '" + pdfDir
                            + "' were specified. Overwriting text files: " + exec.getOverwriteTextfiles());
                    System.err.println("<Ctrl-C> to stop, <Enter> to continue");
                    System.in.read();
                    if (null == exec.isTokenize()) {
                        log.warn("Warning: tokenize parameter not set. Defaulting to true for text extraction and all algorithms to be applied on extracted texts");
                        exec.setTokenize(true);    
                    }
                    exec.setInputFiles(convertPDF(postFiles(pdfDir, "application/pdf"), exec.getStartPage(), exec.isRemoveBib(), exec.getOverwriteTextfiles(), exec.isTokenize(), exec.getTokenizeNLs(), exec.getPtb3Escaping()));
                } else {
                    exec.setInputFiles(postFiles(textDir, "text/plain"));
                }
            }
            //extract the metadata and create the according entities
            if (metaDir != null) {
                List<String> files = new ArrayList<>();
                for (File f : metaDir.toFile().listFiles()) {
                    files.add(f.getAbsolutePath());
                }
                exec.setMetaDataFiles(files);                
            }
        }
    }

    /**
     * checks whether all the variables in the Json file are indeed variables
     * which can be set in the execution.
     *
     * @param o
     * @return
     */
    private void checkJsonFile(JsonObject o) {
        List<String> badFields = new ArrayList<>();
        Execution testExecution = new Execution();
        for (Entry<String, JsonValue> values : o.entrySet()) {
            if (values.getKey().equals("inputFiles")) {
                throwCLI("Do not specify inputFiles in JSON, it will be overridden [in " + json + "]");
            }

            try {
                testExecution.getClass().getDeclaredField(values.getKey());
            } catch (NoSuchFieldException ex) {
            	try {
            		testExecution.getClass().getSuperclass().getDeclaredField(values.getKey());
            	} catch (NoSuchFieldException ex2) {
            		badFields.add(values.getKey());
            	}
            }
        }
        if (!badFields.isEmpty()) {
            throwCLI("Unknown fields: " + badFields);
        }
    }
    
    /**
     * Converts a list of InfolisFile to text
     *
     * @param uris URIs of the InfolisFiles
     * @return URIs of the InfolisFiles of the text versions
     */
    private List<String> convertPDF(List<String> uris, int startPage, boolean removeBib, boolean overwriteTextfiles, boolean tokenize, boolean tokenizeNLs, boolean ptb3Escaping) {
        Execution convertExec = new Execution();
        convertExec.setAlgorithm(TextExtractor.class);
        convertExec.setOutputDirectory(textDir.toString());
        convertExec.setInputFiles(uris);
        convertExec.setStartPage(startPage);
        convertExec.setRemoveBib(removeBib);
        convertExec.setOverwriteTextfiles(overwriteTextfiles);
        convertExec.setTokenize(tokenize);
        convertExec.setTokenizeNLs(tokenizeNLs);
        convertExec.setPtb3Escaping(ptb3Escaping);
        convertExec.setTags(new HashSet<>(Arrays.asList(tag)));
        Algorithm algo = convertExec.instantiateAlgorithm(dataStoreClient, fileResolver);
        algo.run();
        return convertExec.getOutputFiles();
    }

    public List<String> postFiles(Path dir, String mimetype) {
        List<InfolisFile> infolisFiles = new ArrayList<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir)) {
            for (Path file : dirStream) {
                InfolisFile infolisFile = new InfolisFile();

                try (InputStream inputStream = Files.newInputStream(file)) {
                    byte[] bytes = IOUtils.toByteArray(inputStream);
                    infolisFile.setMd5(SerializationUtils.getHexMd5(bytes));
                } catch (IOException e) {
                    throwCLI("Could not read file " + file, e);
                }

                infolisFile.setFileName(file.toString());
                infolisFile.setMediaType(mimetype);
                infolisFile.setTags(new HashSet<>(Arrays.asList(tag)));
                infolisFile.setFileStatus("AVAILABLE");
                
                Entity entity = new Entity();
                entity.setEntityType(EntityType.publication);
                dataStoreClient.post(Entity.class, entity);
                infolisFile.setManifestsEntity(entity.getUri());
                
                infolisFiles.add(infolisFile);
            }
        } catch (IOException e) {
            throwCLI("Couldn't list directory contents of " + dir, e);
        }

        return dataStoreClient.post(InfolisFile.class, infolisFiles);
    }

    private static void throwCLI(String msg) {
        throwCLI(msg, null);
    }

    private static void throwCLI(String msg, Exception e) {
        if (null != msg) {
            System.err.println("**ERROR** " + msg);
        }
        if (null != e) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
        }
        if (System.getProperty("testing") == null) {
            System.exit(1);
        } else {
            log.error("ERROR: {}", e);
        }
        throw new RuntimeException(e);
    }

    public void doMain(String args[]) throws FileNotFoundException, ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException, IOException, CmdLineException {
        CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withUsageWidth(120));
        try {
            parser.parseArgument(args);
            if (null == json && !(shouldConvertToText || searchCandidatesMode)) {
                throwCLI("Must specify JSON if not --convert-to-text|--search-candidates");
            }
            if (null == json && shouldConvertToText && !searchCandidatesMode) {
                convertToTextMode = true;
            }
            // ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            // root.setLevel(Level.toLevel(logLevel));
        } catch (CmdLineException e) {
            System.err.println("java " + getClass().getSimpleName() + " [options...]");
            parser.printUsage(System.err);
            throwCLI("", e);
            return;
        }

        Files.createDirectories(dbDir);

        Execution exec = new Execution();
        exec.setTags(new HashSet<String>(Arrays.asList(tag)));

        // if no JSON was provided, only convert files and exit
        if (null != json) {
            try (Reader reader = Files.newBufferedReader(json, Charset.forName("UTF-8"))) {
                JsonObject jsonObject = Json.createReader(reader).readObject();

                // Check the JSON
                checkJsonFile(jsonObject);

                // Set the other options from JSON
                setExecutionFromJSON(jsonObject, exec);
            } catch (IOException e) {
                throwCLI("Problem reading JSON " + json, e);
            }
        }
        doExecute(exec);
    }

    public static void main(String args[]) throws Exception {
        new CommandLineExecuter().doMain(args);
    }
}
