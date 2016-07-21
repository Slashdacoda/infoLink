package io.github.infolis.algorithm;

import java.util.Arrays;
import java.util.HashSet;

import io.github.infolis.InfolisBaseTest;
import io.github.infolis.model.Execution;
import io.github.infolis.model.TextualReference;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import io.github.infolis.model.entity.Entity;

/**
 *
 * @author kata
 * 
 */
public class MetaDataExtractorTest extends InfolisBaseTest {
	
	@Test
	public void testExecute() {
		Execution exec = new Execution();
		exec.setAlgorithm(MetaDataExtractor.class);
		TextualReference reference = new TextualReference("In this snippet, the reference", "ALLBUS 2000", "is to be extracted as", "document", "pattern","ref");
		dataStoreClient.post(TextualReference.class, reference);
		exec.setTextualReferences(Arrays.asList(reference.getUri()));
		exec.instantiateAlgorithm(dataStoreClient, fileResolver).run();
		Entity entity = dataStoreClient.get(Entity.class, exec.getLinkedEntities().get(0));
		assertEquals("ALLBUS", entity.getName());
        assertEquals("2000", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("2000")), new HashSet<>(entity.getNumericInfo()));
	}

    @Test
    public void testExtractMetadata() {
        MetaDataExtractor mde = new MetaDataExtractor(dataStoreClient, dataStoreClient, fileResolver, fileResolver);
        Entity entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "ALLBUS 2000", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals("2000", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("2000")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 2000", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals("2000", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("2000")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "ALLBUS", "2000 is to be extracted", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals("2000", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("2000")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "Eurobarometer 56.1", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals("56.1", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("56.1")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 56.1", "Eurobarometer", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals("56.1", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("56.1")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "Eurobarometer", "56.1 is to be extracted", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals("56.1", entity.getNumericInfo().get(0));
        assertEquals(new HashSet<>(Arrays.asList("56.1")), new HashSet<>(entity.getNumericInfo()));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "Eurobarometer 56.1 2000", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("56.1", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("reference to the 56.1 2000", "Eurobarometer", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("56.1", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "Eurobarometer", "56.1 2000 is to be", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("56.1", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference 2000", "Eurobarometer", "56.1 is to be", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("56.1", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "ALLBUS 1996/08", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1996/08")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1996/08", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982   -   1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982   -   1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982   -   1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "ALLBUS", "85/01 is to be extracted", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("85/01")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("85/01", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982 till 1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982 till 1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982 till 1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982 to 1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982 to 1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982 to 1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982 bis 1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982 bis 1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982 bis 1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982 und 1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982 und 1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982 und 1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 1982 and 1983", "ALLBUS", "is to be extracted as", "document", "pattern","ref"));
        assertEquals("ALLBUS", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("1982 and 1983")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("1982 and 1983", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 2nd wave of the", "2000 Eurobarometer", "56.1 is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("2", "2000", "56.1")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 2nd wave of the", "Eurobarometer", "2000 is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("2", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 2nd wave of the", "Eurobarometer", "56.1 is to be extracted as", "document", "pattern","ref"));
        assertEquals("Eurobarometer", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("2", "56.1")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("56.1", entity.getNumericInfo().get(0));
        assertEquals("", entity.getIdentifier());
        assertEquals("", entity.getURL());
        
        entity = mde.extractMetadata(new TextualReference("the reference to the 2nd wave of the", "10.4232/1.2525", "2000 is to be extracted as", "document", "pattern","ref"));
        assertEquals("", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList("2", "2000")), new HashSet<>(entity.getNumericInfo()));
        assertEquals("2000", entity.getNumericInfo().get(0));
        assertEquals("10.4232/1.2525", entity.getIdentifier());
        
        entity = mde.extractMetadata(new TextualReference("In this snippet, the reference", "Studierendensurvey", "of any year is to", "document", "pattern","ref"));
        assertEquals("Studierendensurvey", entity.getName());
        assertEquals(new HashSet<>(Arrays.asList()), new HashSet<>(entity.getNumericInfo()));
        assertEquals(0, entity.getNumericInfo().size());
        assertEquals("", entity.getIdentifier());
    }
}
