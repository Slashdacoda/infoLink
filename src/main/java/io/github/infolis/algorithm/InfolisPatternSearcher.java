/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.infolis.algorithm;

import io.github.infolis.datastore.DataStoreClient;
import io.github.infolis.datastore.DataStoreClientFactory;
import io.github.infolis.datastore.DataStoreStrategy;
import io.github.infolis.datastore.FileResolver;
import io.github.infolis.model.Execution;
import io.github.infolis.model.ExecutionStatus;
import io.github.infolis.model.TextualReference;
import io.github.infolis.model.entity.InfolisPattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 *
 * @author kata
 *
 */
public class InfolisPatternSearcher extends BaseAlgorithm {

    public InfolisPatternSearcher(DataStoreClient inputDataStoreClient, DataStoreClient outputDataStoreClient, FileResolver inputFileResolver, FileResolver outputFileResolver) {
        super(inputDataStoreClient, outputDataStoreClient, inputFileResolver, outputFileResolver);
    }

    private static final Logger log = LoggerFactory.getLogger(InfolisPatternSearcher.class);
    
    private List<InfolisPattern> getInfolisPatterns(Collection<String> patternUris) {
    	List<InfolisPattern> patterns = new ArrayList<>();
    	for (String uri : patternUris) {
    		patterns.add(getInputDataStoreClient().get(InfolisPattern.class, uri));
    	}
    	return patterns;
    }
    
    private Multimap<InfolisPattern, String> getTextRefsForLuceneQueries(Collection<InfolisPattern> patterns) {
        HashMultimap<InfolisPattern, String> textRefsForPatterns = HashMultimap.create();
        for (InfolisPattern curPat : patterns) {
    		debug(log, "Lucene pattern: " + curPat.getLuceneQuery());
			try { debug(log, "Regex: " + curPat.getMinimal()); }
			catch (UnknownFormatConversionException e) { debug(log, e.getMessage()); }
			catch (MissingFormatArgumentException e) { debug(log, e.getMessage()); }

        	Execution exec = getExecution().createSubExecution(LuceneSearcher.class);
        	exec.setIndexDirectory(getExecution().getIndexDirectory());
        	exec.setPhraseSlop(getExecution().getPhraseSlop());
        	exec.setAllowLeadingWildcards(getExecution().isAllowLeadingWildcards());
        	exec.setMaxClauseCount(getExecution().getMaxClauseCount());
        	exec.setSearchQuery(curPat.getLuceneQuery());
        	exec.setInputFiles(getExecution().getInputFiles());
    		// TODO LuceneSearcher posts textual references but they are temporary
        	exec.instantiateAlgorithm(this).run();
    		for (String textRefUri : exec.getTextualReferences()) {
    			textRefsForPatterns.put(curPat, textRefUri);
    		}
        }
        return textRefsForPatterns;
    }
    
    private static String getReference(String text, String regex) {
    	Pattern p = Pattern.compile(regex);
    	Matcher m = p.matcher(text);
    	if (m.find()) return m.group(1);
    	return "";
    }

    private static boolean satisfiesUpperCaseConstraint(String string) {
    	// do not treat -RRB-, -LRB- and *NL* tokens as uppercase words
    	return !(string.replaceAll("-RRB-", "").replaceAll("-LRB-", "")
    			.replaceAll("\\*NL\\*", "").toLowerCase()
    			.equals(string.replaceAll("-RRB-", "")
    					.replaceAll("-LRB-", "")
    					.replaceAll("\\*NL\\*", "")));
    }
    
    /**
     * Retrieves contexts for InfolisPatterns using LuceneSearcher and validates them using 
     * the patterns' regular expressions. Validation is necessary because 
     * <ul>
     * <li>lucene queries in the InfolisPatterns may have wildcards for words that must match 
     * a regular expression, e.g. consist of digits only</li>
     * <li>finding named entities consisting of more than one word is enabled using lucene's 
     * phraseSlop parameter. This fuzzy matching may cause text snippets to match that are 
     * not supposed to match</li>
     * <li>lucene's Highlighters perform approximate matching of queries and text. Highlighted 
     * snippets may not always truely contain a match</li>
     * </ul>
     * @param patterns
     * @return
     */
    private List<String> getContextsForPatterns(Collection<InfolisPattern> patterns) {
        int counter = 0, size = patterns.size();
        log.debug("number of patterns to search for: " + size);
    	// for all patterns, retrieve documents in which they occur (using lucene)
    	Multimap<InfolisPattern, String> textRefsForPatterns = getTextRefsForLuceneQueries(
    			patterns);
    	List<String> validatedTextualReferences = new ArrayList<>();
    	// open each reference once and validate with the corresponding regular expression
    	for (InfolisPattern pattern : textRefsForPatterns.keySet()) {
    		Collection<TextualReference> textualReferences = getInputDataStoreClient().get(
    				TextualReference.class, textRefsForPatterns.get(pattern));
    		for (TextualReference textRef : textualReferences) {
    			// textual reference does not match regex
    			String referencedTerm = getReference(textRef.getLeftText(), pattern.getMinimal());
	    		if ("".equals(referencedTerm)) {
	    			log.debug("Textual reference does not match regex: " + pattern.getMinimal());
	    			log.debug("Textual reference: " + textRef.getLeftText());
	    			continue;
	    		}
	    		if ((getExecution().isUpperCaseConstraint() && 
	    				!satisfiesUpperCaseConstraint(referencedTerm))) {
	    			log.debug("Referenced term does not satisfy uppercase-constraint \"" + 
	    					referencedTerm + "\"");
	    			continue;
	    		}
	    		// if referencedTerm contains no characters: ignore
                // TODO: not accurate - include accents etc in match... \p{M}?
                if (referencedTerm.matches("\\P{L}+")) {
                    log.debug("Invalid referenced term \"" + referencedTerm + "\"");
                    continue;
                }
                TextualReference validatedTextRef = LuceneSearcher.getContext(referencedTerm, textRef.getLeftText(), textRef.getFile(), pattern.getUri(), textRef.getMentionsReference());
                getOutputDataStoreClient().post(TextualReference.class, validatedTextRef);
                validatedTextualReferences.add(validatedTextRef.getUri());
                //TODO delete textRef!
    		}
    		counter++;
    		updateProgress(counter, size);
    	}
        return validatedTextualReferences;
    }

    Execution createIndex() throws IOException {
		Execution execution = getExecution().createSubExecution(Indexer.class);
		execution.setInputFiles(getExecution().getInputFiles());
        getOutputDataStoreClient().post(Execution.class, execution);
        execution.instantiateAlgorithm(this).run();
		return execution;
	}
    
    @Override
    public void execute() throws IOException {
    	Execution tagExec = new Execution();
    	tagExec.setAlgorithm(TagSearcher.class);
    	tagExec.getInfolisFileTags().addAll(getExecution().getInfolisFileTags());
    	tagExec.getInfolisPatternTags().addAll(getExecution().getInfolisPatternTags());
    	tagExec.instantiateAlgorithm(this).run();
    	getExecution().getPatterns().addAll(tagExec.getPatterns());
    	getExecution().getInputFiles().addAll(tagExec.getInputFiles());
    	
    	if (null == getExecution().getIndexDirectory() || getExecution().getIndexDirectory().isEmpty()) {
    		debug(log, "No index directory specified, indexing on demand");
    		Execution indexerExecution = createIndex();
    		getExecution().setIndexDirectory(indexerExecution.getOutputDirectory());
    	}
    	log.debug("started");
        getExecution().setTextualReferences(getContextsForPatterns(getInfolisPatterns(getExecution().getPatterns())));
        log.debug("No. contexts found: {}", getExecution().getTextualReferences().size());
        getExecution().setStatus(ExecutionStatus.FINISHED);
    }

    @Override
    public void validate() {
    	Execution exec = this.getExecution();
		if ((null == exec.getInputFiles() || exec.getInputFiles().isEmpty()) && 
    		(null == exec.getInfolisFileTags() || exec.getInfolisFileTags().isEmpty())) {
            throw new IllegalArgumentException("Must set at least one inputFile!");
        }
        if ((null == exec.getPatterns() || exec.getPatterns().isEmpty()) && 
        		(null == exec.getInfolisPatternTags() || exec.getInfolisPatternTags().isEmpty())) {
            throw new IllegalArgumentException("No patterns given.");
        }
    }
}
