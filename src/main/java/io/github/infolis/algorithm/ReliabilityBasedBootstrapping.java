/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.infolis.algorithm;

import io.github.infolis.infolink.luceneIndexing.PatternInducer;
import io.github.infolis.infolink.patternLearner.Reliability;
import io.github.infolis.model.Execution;
import io.github.infolis.model.InfolisPattern;
import io.github.infolis.model.StudyContext;
import io.github.infolis.util.InfolisFileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.queryParser.ParseException;
import org.slf4j.LoggerFactory;

/**
 *
 * @author domi
 */
public class ReliabilityBasedBootstrapping extends BaseAlgorithm {
    
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ReliabilityBasedBootstrapping.class);

    private List<StudyContext> bootstrap_reliabilityBased(Collection<String> terms, double threshold, int maxIter) throws IOException, ParseException {
        Set<String> reliableInstances = new HashSet<>();
        Set<InfolisPattern> reliablePatterns_iteration;
        Set<StudyContext> extractedContexts = new HashSet();
        Set<String> processedPattern = new HashSet<>();
        List<StudyContext> reliableContexts = new ArrayList<>();
        int numIter =0;
        Reliability r = new Reliability();
        Set<String> seeds = new HashSet<>();
        seeds.addAll(terms);
        while (numIter<maxIter) {
            numIter++;
            log.debug("Bootstrapping... Iteration: " + numIter);            
            // 0. filter seeds, select only reliable ones
            // alternatively: use all seeds extracted by reliable patterns
            reliableInstances.addAll(seeds);
            // 1. search for all seeds and save contexts
            for (String seed : seeds) {
                log.debug("Bootstrapping with seed " + seed);
                    
                // 1. use lucene index to search for term in corpus
                Execution execution = new Execution();
                execution.setAlgorithm(SearchTermPosition.class);
                execution.setSearchTerm(seed);
                execution.setIndexDirectory(this.getExecution().getIndexDirectory());

                Algorithm algo = new SearchTermPosition();
                algo.run();

                List<StudyContext> detectedContexts = new ArrayList<>();
                for (String sC : execution.getStudyContexts()) {
                    StudyContext context = this.getDataStoreClient().get(StudyContext.class, sC);
                    detectedContexts.add(context);
                    processedPattern.add(context.getPattern().getPatternRegex());
                }
                extractedContexts.addAll(detectedContexts);
            }
            // 2. get reliable patterns, save their data to this.reliablePatternsAndContexts and 
            // new seeds to this.foundSeeds_iteration
            reliablePatterns_iteration = PatternInducer.saveReliablePatternData(extractedContexts, threshold, processedPattern, getExecution().getInputFiles().size(),reliableInstances,r);
            seeds = new HashSet<>();
            for (StudyContext sc : extractedContexts) {
                if(reliablePatterns_iteration.contains(sc.getPattern())) {
                    seeds.add(sc.getTerm());
                    reliableContexts.add(sc);
                }
            }
        }
        return reliableContexts;
    }

    @Override
    public void execute() throws IOException {
        List<StudyContext> detectedContexts = new ArrayList<>();
        try {
            detectedContexts = bootstrap_reliabilityBased(this.getExecution().getTerms(), this.getExecution().getThreshold(), this.getExecution().getMaxIterations());
        } catch (Exception ex) {
            log.error("Could not apply reliability bootstrapping: " + ex);
        }

        for (StudyContext sC : detectedContexts) {
            getDataStoreClient().post(StudyContext.class, sC);
            this.getExecution().getStudyContexts().add(sC.getUri());
        }
        for (StudyContext sC : detectedContexts) {
            getDataStoreClient().post(StudyContext.class, sC);
            this.getExecution().getPattern().add(sC.getPattern().getUri());
        }
    }

    @Override
    public void validate() {
        //TODO: what about the index path? need to be given!
        if (null == this.getExecution().getTerms()
                || this.getExecution().getTerms().isEmpty()) {
            throw new IllegalArgumentException("Must set at least one term as seed!");
        }
        if (null == this.getExecution().getInputFiles()
                || this.getExecution().getInputFiles().isEmpty()) {
            throw new IllegalArgumentException("Must set at least one input file!");
        }
    }

}