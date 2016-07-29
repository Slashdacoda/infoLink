package io.github.infolis.algorithm;

import io.github.infolis.datastore.DataStoreClient;
import io.github.infolis.datastore.FileResolver;
import io.github.infolis.infolink.patternLearner.BootstrapLearner;
import io.github.infolis.model.Execution;
import io.github.infolis.model.ExecutionStatus;
import io.github.infolis.model.TextualReference;
import io.github.infolis.model.entity.InfolisFile;
import io.github.infolis.model.entity.InfolisPattern;
import io.github.infolis.util.RegexUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.LoggerFactory;

import com.google.common.net.MediaType;

/**
 *
 * @author kata
 *
 */
public abstract class Bootstrapping extends BaseAlgorithm implements BootstrapLearner {

    public Bootstrapping(DataStoreClient inputDataStoreClient, DataStoreClient outputDataStoreClient, FileResolver inputFileResolver, FileResolver outputFileResolver) throws IOException {
		super(inputDataStoreClient, outputDataStoreClient, inputFileResolver, outputFileResolver);
	}
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Bootstrapping.class);
    public Execution indexerExecution;

    public abstract List<TextualReference> bootstrap() throws ParseException, IOException, InstantiationException, IllegalAccessException;

    public abstract static class PatternInducer {
    	protected abstract List<InfolisPattern> induce(TextualReference context, Double[] thresholds);
    	public abstract int getPatternsPerContext();
    };
    // TODO define getBestPatterns - method
    public abstract class PatternRanker {};

    Execution createIndex() throws IOException {
		Execution execution = getExecution().createSubExecution(Indexer.class);
		execution.setInputFiles(getExecution().getInputFiles());
		execution.setOutputDirectory(getExecution().getIndexDirectory());
        execution.instantiateAlgorithm(this).run();
        getOutputDataStoreClient().post(Execution.class, execution);
		return execution;
	}

    List<TextualReference> getContextsForSeed(String seed) {
        // use lucene index to search for term in corpus
        Execution execution = getExecution().createSubExecution(LuceneSearcher.class);
        execution.setIndexDirectory(indexerExecution.getOutputDirectory());
        execution.setPhraseSlop(0);
        execution.setAllowLeadingWildcards(true);
        execution.setMaxClauseCount(getExecution().getMaxClauseCount());
        execution.setSearchTerm(seed);
        InfolisPattern termPattern = new InfolisPattern(RegexUtils.normalizeQuery(seed, true));
        DataStoreClient tempClient = getTempDataStoreClient();
        tempClient.post(InfolisPattern.class, termPattern);
        execution.setPatterns(Arrays.asList(termPattern.getUri()));
        execution.setInputFiles(getExecution().getInputFiles());
        execution.setReliabilityThreshold(getExecution().getReliabilityThreshold());
        Algorithm algo = execution.instantiateAlgorithm(
        		getInputDataStoreClient(), tempClient, getInputFileResolver(), getOutputFileResolver());
        algo.run();
        getOutputDataStoreClient().post(Execution.class, execution);
        getExecution().getLog().addAll(execution.getLog());
        List<TextualReference> textualReferences = new ArrayList<>();
        for (String uri : execution.getTextualReferences()) {
           	textualReferences.add(tempClient.get(TextualReference.class, uri));
        }
        tempClient.clear();
        return textualReferences;
    }

    private List<String> getPatternUris(Collection<InfolisPattern> patternList) {
    	List<String> patternUris = new ArrayList<String>();
    	for (InfolisPattern curPat : patternList) {
	    	if (curPat.getUri() == null)
	    		throw new RuntimeException("Pattern does not have a URI!");
	    	patternUris.add(curPat.getUri());
	    	log.debug("pattern " + curPat + " has uri " + curPat.getUri());
    	}
    	return patternUris;
    }

    List<String> getContextsForPatterns(Collection<InfolisPattern> patterns, 
    		DataStoreClient outputDataStoreClient) {
    	Execution applierExec = new Execution();
    	applierExec.setAlgorithm(InfolisPatternSearcher.class);
    	applierExec.setInputFiles(getExecution().getInputFiles());
    	applierExec.setIndexDirectory(indexerExecution.getOutputDirectory());
    	applierExec.setPatterns(getPatternUris(patterns));
    	applierExec.setUpperCaseConstraint(getExecution().isUpperCaseConstraint());
    	applierExec.setPhraseSlop(getExecution().getPhraseSlop());
    	applierExec.setAllowLeadingWildcards(true);
    	applierExec.setMaxClauseCount(getExecution().getMaxClauseCount());
    	applierExec.setTags(getExecution().getTags());
    	applierExec.instantiateAlgorithm(getInputDataStoreClient(), outputDataStoreClient, 
    			getInputFileResolver(), getOutputFileResolver()).run();
    	return applierExec.getTextualReferences();
    }

    @Override
    public void validate() {
        Execution exec = this.getExecution();
		if (null == exec.getSeeds() || exec.getSeeds().isEmpty()) {
            throw new IllegalArgumentException("Must set at least one term as seed!");
        }
        if ((null == exec.getInputFiles() || exec.getInputFiles().isEmpty()) &&
    		(null == exec.getInfolisFileTags() || exec.getInfolisFileTags().isEmpty())) {
            throw new IllegalArgumentException("Must set at least one input file!");
        }
        if (null == exec.getBootstrapStrategy()) {
            throw new IllegalArgumentException("Must set the bootstrap strategy!");
        }
        if (null == exec.isTokenize()) {
			warn(log, "tokenize parameter unspecified. Setting to true for Bootstrapping"); 
			exec.setTokenize(true);
		}
    }

    @Override
    public void execute() throws IOException {
    	Execution tagExec = getExecution().createSubExecution(TagSearcher.class);
    	tagExec.getInfolisFileTags().addAll(getExecution().getInfolisFileTags());
    	tagExec.getInfolisPatternTags().addAll(getExecution().getInfolisPatternTags());
    	tagExec.instantiateAlgorithm(this).run();

    	getExecution().getPatterns().addAll(tagExec.getPatterns());
    	getExecution().getInputFiles().addAll(tagExec.getInputFiles());
    	
    	List<String> toTextExtract = new ArrayList<>();
    	List<String> toTokenize = new ArrayList<>();
    	List<String> toBibExtract = new ArrayList<>();
    	
    	for (InfolisFile file : getInputDataStoreClient().get(
    			InfolisFile.class, getExecution().getInputFiles())) {
    		if (file.getMediaType().equals(MediaType.PDF.toString())) {
    			toTextExtract.add(file.getUri());
    			getExecution().getInputFiles().remove(file.getUri());
    		}
    	}
    	
    	if (!toTextExtract.isEmpty()) {
    		Execution textExtract = getExecution().createSubExecution(TextExtractor.class);
    		textExtract.setTokenize(getExecution().isTokenize());
    		textExtract.setRemoveBib(getExecution().isRemoveBib());
    		textExtract.setTags(getExecution().getTags());
    		textExtract.instantiateAlgorithm(this).run();
    		getExecution().getInputFiles().addAll(textExtract.getOutputFiles());
    	}
    	
    	if (getExecution().isTokenize() || getExecution().isRemoveBib()) {
	    	for (InfolisFile file : getInputDataStoreClient().get(
	    			InfolisFile.class, getExecution().getInputFiles())) {
	    		// if input file isn't tokenized, apply tokenizer
	    		// TODO tokenizer parameters also relevant...
	    		if (getExecution().isTokenize()) {
		    		if (!file.getTags().contains(Tokenizer.getExecutionTags().get(0))) {
		    			toTokenize.add(file.getUri());
		    			getExecution().getInputFiles().remove(file.getUri());
		    		}
	    		}
	    		// removing bibliographies is optional
	    		// if it is to be performed, check whether input files are stripped of 
	    		// their bibliography sections already
	    		if (getExecution().isRemoveBib()) {
		    		if (!file.getTags().contains(BibliographyExtractor.getExecutionTags().get(0))) {
		    			toBibExtract.add(file.getUri());
		    			getExecution().getInputFiles().remove(file.getUri());
		    		}
	    		}
	    	}
	
	    	if (getExecution().isRemoveBib() && !toBibExtract.isEmpty()) {
	    		Execution bibRemoverExec = getExecution().createSubExecution(BibliographyExtractor.class);
	    		bibRemoverExec.setTags(getExecution().getTags());
	    		for (String uri : toBibExtract) {
	    			bibRemoverExec.setInputFiles(Arrays.asList(uri));
	    			bibRemoverExec.instantiateAlgorithm(this).run();
	    			debug(log, "Removed bibliographies of input file: " + uri);
	    			if (!toTokenize.contains(uri)) {
	    				getExecution().getInputFiles().add(bibRemoverExec.getOutputFiles().get(0));
	    			}
	    			else {
	    				toTokenize.remove(uri);
	    				toTokenize.add(bibRemoverExec.getOutputFiles().get(0));
	    			}
	    		}
	    	}
	    	
	    	if (getExecution().isTokenize() && !toTokenize.isEmpty()) {
		    	Execution tokenizerExec = getExecution().createSubExecution(TokenizerStanford.class);
		    	tokenizerExec.setTags(getExecution().getTags());
		    	tokenizerExec.setTokenizeNLs(getExecution().getTokenizeNLs());
		    	tokenizerExec.setPtb3Escaping(getExecution().getPtb3Escaping());
		    	tokenizerExec.setInputFiles(toTokenize);
		    	tokenizerExec.instantiateAlgorithm(this).run();
		    	debug(log, "Tokenized input with parameters tokenizeNLs=" + tokenizerExec.getTokenizeNLs() + " ptb3Escaping=" + tokenizerExec.getPtb3Escaping());
		    	getExecution().getInputFiles().addAll(tokenizerExec.getOutputFiles());
	    	}
    	}
    	
    	this.indexerExecution = createIndex();
    	List<TextualReference> detectedContexts = new ArrayList<>();
        try {
        	detectedContexts = bootstrap();
        } catch (ParseException | IOException | InstantiationException | IllegalAccessException ex) {
            log.error("Could not apply reliability bootstrapping: " + ex);
            getExecution().setStatus(ExecutionStatus.FAILED);
        }

        for (TextualReference sC : detectedContexts) {
            //getOutputDataStoreClient().post(TextualReference.class, sC);
            this.getExecution().getTextualReferences().add(sC.getUri());
            this.getExecution().getPatterns().add(sC.getPattern());
        }
        getExecution().setStatus(ExecutionStatus.FINISHED);
    }

}
