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
import io.github.infolis.datastore.FileResolverFactory;
import io.github.infolis.model.Execution;
import io.github.infolis.model.InfolisFile;
import io.github.infolis.model.InfolisPattern;
import io.github.infolis.util.SerializationUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author domi
 */
public class FrequencyBootstrappingTest {
    
    private final static DataStoreClient client = DataStoreClientFactory.local();
    private final static FileResolver fileResolver = FileResolverFactory.create(DataStoreStrategy.TEMPORARY);
    Logger log = LoggerFactory.getLogger(SearchTermPositionTest.class);

    List<String> pattern = new ArrayList<>();
    List<String> files = new ArrayList<>();
    
	private final static List<String> testStrings = Arrays.asList(
			"Please try to find the ALLBUS 2003 short text snippet that I provided to you.",
			"Please try to find the allbus in this short text snippet that I provided to you.",
			"Please try to find the .allbus. in this short text snippet that I provided to you.",
                        "Please try to find the ALLBUS in this short text snippet that I provided to you."
			);
        private final static List<String> terms = Arrays.asList("allbus");
        

	private final List<String> testFiles = new ArrayList<>();

	@Before
    public void createInputFiles() throws IOException {
		testFiles.clear();
    	for (String str : testStrings) {
    		String hexMd5 = SerializationUtils.getHexMd5(str);
    		InfolisFile file = new InfolisFile();
    		file.setMd5(hexMd5);
    		OutputStream outputStream = fileResolver.openOutputStream(file);
    		IOUtils.write(str, outputStream);
    		client.post(InfolisFile.class, file);
    		testFiles.add(file.getUri());
    	}       
    }

    @Test
    public void testBootstrapping() throws Exception {
    	
    	Execution execution = new Execution();   
    	execution.getTerms().addAll(terms);
    	execution.setAlgorithm(FrequencyBasedBootstrapping.class);
    	execution.getInputFiles().addAll(testFiles);
        
        Algorithm algo = new FrequencyBasedBootstrapping();
    	algo.setDataStoreClient(client);
    	algo.setFileResolver(fileResolver);
    	algo.setExecution(execution);
    	algo.run();        
    	
    	System.out.println(execution.getStudyContexts());
        System.out.println(execution.getPattern());
    }
    
}