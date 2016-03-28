package io.github.infolis.algorithm;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.infolis.datastore.DataStoreClient;
import io.github.infolis.datastore.FileResolver;
import io.github.infolis.model.Execution;
import io.github.infolis.model.entity.InfolisFile;
import io.github.infolis.util.SerializationUtils;
import opennlp.tools.util.InvalidFormatException;

/**
 *  
 * @author kata
 *
 */
public abstract class Tokenizer extends BaseAlgorithm {

	public Tokenizer (DataStoreClient inputDataStoreClient, DataStoreClient outputDataStoreClient, FileResolver inputFileResolver, FileResolver outputFileResolver) throws IOException {
		super(inputDataStoreClient, outputDataStoreClient, inputFileResolver, outputFileResolver);
	}
	
	private static final Logger log = LoggerFactory.getLogger(Tokenizer.class);
	
	/**
	 * Splits text into sentences and tokenizes all words.
	 * 
	 * @param text
	 * @return
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public abstract List<String> getTokenizedSentences(String text) throws InvalidFormatException, IOException;
	
	/**
	 * Splits text in file into sentences and tokenizes all words.
	 * 
	 * @param file
	 * @return
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public abstract List<String> getTokenizedSentences(File file) throws InvalidFormatException, IOException;
	
	public String getTokenizedText(List<String> tokenizedSentences) {
		return String.join(System.getProperty("line.separator"), tokenizedSentences);
	}
	
	private String transformFilename(String filename, String outputDir) {
   	 String outFileName = SerializationUtils.changeFileExtension(filename, "tokenized.txt");
        if (null != outputDir && !outputDir.isEmpty()) {
            outFileName = SerializationUtils.changeBaseDir(outFileName, outputDir);
        }
        return outFileName;
   }
	
	public String createInfolisFile(String filename, List<String> tokenizedSentences, Set<String> tags) throws IOException {
		InfolisFile infolisFile = new InfolisFile();
		String outFileName = transformFilename(filename, getExecution().getOutputDirectory());
        String asText = String.join(System.getProperty("line.separator"), tokenizedSentences);
        infolisFile.setFileName(outFileName);
        infolisFile.setMediaType("text/plain");
		infolisFile.setTags(tags);
		infolisFile.setMd5(SerializationUtils.getHexMd5(asText));
        infolisFile.setFileStatus("AVAILABLE");
        
        try (OutputStream outStream = getOutputFileResolver().openOutputStream(infolisFile)) {
            try {
                IOUtils.write(asText, outStream);
            } catch (IOException e) {
                warn(log, "Error copying text to output stream: " + e);
                throw e;
            }
        } catch (IOException e) {
            warn(log, "Error opening output stream to text file: " + e);
            throw e;
        }
        
        getOutputDataStoreClient().post(InfolisFile.class, infolisFile);
        return infolisFile.getUri();
	}
	
	@Override
	public void execute() throws IOException {
		Execution tagExec = getExecution().createSubExecution(TagSearcher.class);
    	tagExec.getInfolisFileTags().addAll(getExecution().getInfolisFileTags());
    	tagExec.instantiateAlgorithm(this).run();
    	getExecution().getInputFiles().addAll(tagExec.getInputFiles());
    	
    	for (String inputFileURI : getExecution().getInputFiles()) {
    		InfolisFile infolisFile = getInputDataStoreClient().get(InfolisFile.class, inputFileURI);
    		// TODO update status
    		List<String> tokenizedSentences = getTokenizedSentences(new File(infolisFile.getFileName()));
    		Set<String> tagsToSet = getExecution().getTags();
    		tagsToSet.addAll(infolisFile.getTags());
    		String outputFileURI = createInfolisFile(infolisFile.getFileName(), tokenizedSentences, tagsToSet);
    		getExecution().getOutputFiles().add(outputFileURI);
    	}
	}
	
	@Override
	//TODO
	public void validate() {
		
	}

}