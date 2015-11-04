package io.github.infolis.scheduler;

import io.github.infolis.InfolisBaseTest;
import io.github.infolis.algorithm.Algorithm;
import io.github.infolis.algorithm.ApplyPatternAndResolve;
import io.github.infolis.algorithm.PatternApplier;
import io.github.infolis.algorithm.TextExtractorAlgorithm;
import io.github.infolis.datastore.DataStoreClient;
import io.github.infolis.datastore.DataStoreClientFactory;
import io.github.infolis.datastore.DataStoreStrategy;
import io.github.infolis.datastore.FileResolver;
import io.github.infolis.datastore.FileResolverFactory;
import io.github.infolis.model.Execution;
import io.github.infolis.model.entity.InfolisFile;
import io.github.infolis.model.entity.InfolisPattern;
import io.github.infolis.util.SerializationUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author domi
 */
public class ExecutionSchedulerTest extends InfolisBaseTest {

    private byte[] pdfBytes;
    Path tempFile;

    @Before
    public void setUp() throws IOException {
        dataStoreClient.clear();
        pdfBytes = IOUtils.toByteArray(getClass().getResourceAsStream("/trivial.pdf"));
        tempFile = Files.createTempFile("infolis-", ".pdf");
    }

    @Test
    public void testScheduler() throws IOException, InterruptedException {
        File txtDir = new File(getClass().getResource("/examples/txts").getFile());
        File patternFile = new File(getClass().getResource("/examples/pattern.txt").getFile());

        //post all improtant stuff
        List<String> pattern = postPattern(patternFile);
        List<String> txt = postTxtFiles(txtDir);

        Execution e = new Execution();
        e.setAlgorithm(PatternApplier.class);
        e.setPatternUris(pattern);
        e.setInputFiles(txt);
        dataStoreClient.post(Execution.class, e);
        ExecutionScheduler exe = ExecutionScheduler.getInstance();
        exe.execute(e.instantiateAlgorithm(dataStoreClient, fileResolver));

        System.out.println("open first: " +exe.getOpenExecutions().size());
        
        InfolisFile inFile = new InfolisFile();
        Execution execution = new Execution();
        inFile.setFileName(tempFile.toString());
        inFile.setMd5(SerializationUtils.getHexMd5(pdfBytes));
        inFile.setMediaType("application/pdf");
        inFile.setFileStatus("AVAILABLE");
        writeFile(inFile);

        execution.getInputFiles().add(inFile.getUri());
        execution.setAlgorithm(TextExtractorAlgorithm.class);
        dataStoreClient.post(Execution.class, execution);
        //ExecutionScheduler exe= ExecutionScheduler.getInstance();
        Algorithm algo = execution.instantiateAlgorithm(dataStoreClient, fileResolver);
        exe.execute(algo);
        
        exe.shutDown();
        
        System.out.println("open second: " +exe.getOpenExecutions().size());
        System.out.println("complete first: " +exe.getCompletedExecutions().size());
        System.out.println("failed: " +exe.getFailedExecutions().size());
    }

    public List<String> postTxtFiles(File dir) throws IOException {
        List<String> txtFiles = new ArrayList<>();
        for (File f : dir.listFiles()) {
            Path tempFile = Files.createTempFile("infolis-", ".txt");
            InfolisFile inFile = new InfolisFile();
            FileInputStream inputStream = new FileInputStream(f.getAbsolutePath());
            int numberBytes = inputStream.available();
            byte pdfBytes[] = new byte[numberBytes];
            inputStream.read(pdfBytes);
            IOUtils.write(pdfBytes, Files.newOutputStream(tempFile));
            inFile.setFileName(tempFile.toString());
            inFile.setMd5(SerializationUtils.getHexMd5(pdfBytes));
            inFile.setMediaType("text/plain");
            inFile.setFileStatus("AVAILABLE");
            try {
                OutputStream os = fileResolver.openOutputStream(inFile);
                IOUtils.write(pdfBytes, os);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            dataStoreClient.post(InfolisFile.class, inFile);
            txtFiles.add(inFile.getUri());
        }
        return txtFiles;
    }

    public List<String> postPattern(File pattern) throws IOException {
        BufferedReader read = new BufferedReader(new FileReader(pattern));
        String line = read.readLine();
        List<String> postedPattern = new ArrayList<>();
        while (line != null) {
            InfolisPattern p = new InfolisPattern(line);
            dataStoreClient.post(InfolisPattern.class, p);
            postedPattern.add(p.getUri());
            line = read.readLine();
        }
        return postedPattern;
    }
    
    private void writeFile(InfolisFile inFile) {
		dataStoreClient.post(InfolisFile.class, inFile);
		try {
			OutputStream os = fileResolver.openOutputStream(inFile);
			IOUtils.write(pdfBytes, os);
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}