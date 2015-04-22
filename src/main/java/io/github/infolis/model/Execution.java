package io.github.infolis.model;

import io.github.infolis.algorithm.Algorithm;
import io.github.infolis.datastore.DataStoreClientFactory;
import io.github.infolis.datastore.DataStoreStrategy;
import io.github.infolis.datastore.FileResolverFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 *
 * @author domi
 * @author kba
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Execution extends BaseModel {

	private static final Logger logger = LoggerFactory.getLogger(Execution.class);

	private Class<? extends Algorithm> algorithm;
	private ExecutionStatus status = ExecutionStatus.PENDING;
	private List<String> log = new ArrayList<>();
	private Date startTime;
	private Date endTime;

	//
	// Parameters
	//
	private List<String> inputFiles = new ArrayList<>();
	private List<String> outputFiles = new ArrayList<>();
	// TextExtractor
	private boolean removeBib = false;
	private String outputDirectory = "";

	// SearchTermPosition
	private int phraseSlop = 10;
	private boolean allowLeadingWildcards = true;
	private int maxClauseCount = Integer.MAX_VALUE;
	private String searchTerm;
	private String searchQuery;
	private List<String> studyContexts = new ArrayList<>();
	private List<String> matchingFilenames = new ArrayList<>();
	private boolean overwrite = false;
	private String indexDirectory;
        private List<String> patterns = new ArrayList<>();
        private boolean upperCaseConstraint = false;
        private boolean requiresContainedInNP = false;
        
	public Algorithm instantiateAlgorithm(DataStoreStrategy dataStoreStrategy)
			throws InstantiationException, IllegalAccessException {
		if (null == this.getAlgorithm()) {
			throw new IllegalArgumentException(
					"Must set 'algorithm' of execution before calling instantiateAlgorithm.");
		}
		Algorithm algo = this.algorithm.newInstance();
		algo.setExecution(this);
		algo.setFileResolver(FileResolverFactory.create(dataStoreStrategy));
		algo.setDataStoreClient(DataStoreClientFactory.create(dataStoreStrategy));
		logger.debug("Created instance for algorithm '{}'", this.getAlgorithm());
		return algo;
	}

	//
	// GETTERS / SETTERS
	//

	public ExecutionStatus getStatus() {
		return status;
	}

	public void setStatus(ExecutionStatus status) {
		this.status = status;
	}

	public List<String> getLog() {
		return log;
	}

	public void setLog(List<String> log) {
		this.log = log;
	}

	public void logFatal(String msg) {
		this.getLog().add(String.format("[%s] %s", "FATAL", msg));
	}

	public void logDebug(String msg) {
		this.getLog().add(String.format("[%s] %s", "DEBUG", msg));
	}

	public List<String> getInputFiles() {
		return inputFiles;
	}

	public void setInputFiles(List<String> paramPdfInput) {
		this.inputFiles = paramPdfInput;
	}

	@JsonIgnore
	public String getFirstInputFile() {
		return inputFiles.get(0);
	}

	@JsonIgnore
	public void setFirstInputFile(String fileName) {
		if (null == inputFiles) {
			inputFiles = new ArrayList<>();
		}
		if (inputFiles.size() > 0) {
			inputFiles.set(0, fileName);
		} else {
			inputFiles.add(fileName);
		}
	}

	public List<String> getOutputFiles() {
		return outputFiles;
	}

	public void setOutputFiles(List<String> paramPdfOutput) {
		this.outputFiles = paramPdfOutput;
	}

	@JsonIgnore
	public String getFirstOutputFile() {
		return outputFiles.get(0);
	}

	@JsonIgnore
	public void setFirstOutputFile(String fileName) {
		if (null == outputFiles) {
			outputFiles = new ArrayList<>();
		}
		if (outputFiles.size() > 0) {
			outputFiles.set(0, fileName);
		} else {
			outputFiles.add(fileName);
		}
	}

	public boolean isRemoveBib() {
		return removeBib;
	}

	public void setRemoveBib(boolean removeBib) {
		this.removeBib = removeBib;
	}

	public String getOutputDirectory() {
		return outputDirectory;
	}

	public void setOutputDirectory(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public Class<? extends Algorithm> getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(Class<? extends Algorithm> algorithm) {
		this.algorithm = algorithm;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public int getPhraseSlop() {
		return phraseSlop;
	}

	public void setPhraseSlop(int phraseSlop) {
		this.phraseSlop = phraseSlop;
	}

	public boolean isAllowLeadingWildcards() {
		return allowLeadingWildcards;
	}

	public void setAllowLeadingWildcards(boolean allowLeadingWildcards) {
		this.allowLeadingWildcards = allowLeadingWildcards;
	}

	public int getMaxClauseCount() {
		return maxClauseCount;
	}

	public void setMaxClauseCount(int maxClauseCount) {
		this.maxClauseCount = maxClauseCount;
	}

	public String getSearchTerm() {
		return searchTerm;
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
	}

	public String getSearchQuery() {
		return searchQuery;
	}

	public void setSearchQuery(String searchQuery) {
		this.searchQuery = searchQuery;
	}

	public List<String> getStudyContexts() {
		return studyContexts;
	}

	public void setStudyContexts(List<String> studyContexts) {
		this.studyContexts = studyContexts;
	}

	public boolean isOverwrite() {
		return overwrite;
	}

	public void setOverwrite(boolean overwrite) {
		this.overwrite = overwrite;
	}

	public List<String> getMatchingFilenames() {
		return matchingFilenames;
	}

	public void setMatchingFilenames(List<String> matchingFilenames) {
		this.matchingFilenames = matchingFilenames;
	}

	public String getIndexDirectory() {
		return indexDirectory;
	}

	public void setIndexDirectory(String indexDirectory) {
		this.indexDirectory = indexDirectory;
	}

    /**
     * @return the pattern
     */
    public List<String> getPattern() {
        return patterns;
    }

    /**
     * @param pattern the pattern to set
     */
    public void setPattern(List<String> pattern) {
        this.patterns = pattern;
    }

    /**
     * @return the upperCaseConstraint
     */
    public boolean isUpperCaseConstraint() {
        return upperCaseConstraint;
    }

    /**
     * @param upperCaseConstraint the upperCaseConstraint to set
     */
    public void setUpperCaseConstraint(boolean upperCaseConstraint) {
        this.upperCaseConstraint = upperCaseConstraint;
    }

    /**
     * @return the requiresContainedInNP
     */
    public boolean isRequiresContainedInNP() {
        return requiresContainedInNP;
    }

    /**
     * @param requiresContainedInNP the requiresContainedInNP to set
     */
    public void setRequiresContainedInNP(boolean requiresContainedInNP) {
        this.requiresContainedInNP = requiresContainedInNP;
    }

}