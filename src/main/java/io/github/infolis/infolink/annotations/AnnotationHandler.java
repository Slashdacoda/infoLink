package io.github.infolis.infolink.annotations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import edu.stanford.nlp.ling.CoreLabel;
import io.github.infolis.algorithm.TokenizerStanford;
import io.github.infolis.infolink.annotations.Annotation.Metadata;
import io.github.infolis.model.TextualReference;

/**
 * 
 * @author kata
 *
 */
public abstract class AnnotationHandler {
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(AnnotationHandler.class);
	private static final Pattern annotatedEntityPat = Pattern.compile("\\*(.*?)\\*\\[\\s+(.+?)\\s+\\]\\*\\*");
	
	public abstract List<Annotation> parse(String input);
	protected abstract Metadata getMetadata(String annotatedItem);
	
	protected String read(String filename) throws IOException{
		return FileUtils.readFileToString(new File(filename));
	}
	
	// one annotated sentence may contain multiple references -> return list
	private static List<TextualReference> createTextualReferencesFromAnnotations(List<Annotation> sentence, 
			Set<Metadata> relevantFields) {
		List<String> text = new ArrayList<>();
		List<TextualReference> textualRefs = new ArrayList<>();
		sentence = mergeNgrams(sentence);
		
		for (int i = 0; i < sentence.size(); i++) {
			Annotation annotation = sentence.get(i); 
			text.add(annotation.getWord());
		}
		
		for (int i = 0; i < sentence.size(); i++) {
			Annotation annotation = sentence.get(i); 
			if (relevantFields.contains(annotation.getMetadata())) {
				TextualReference textRef = new TextualReference();
				// assumes that annotations are locked to token boundaries 
				// -> annotated entities are separated from surrounding words by whitespace
				textRef.setLeftText(String.join(" ", text.subList(0, i)) + " ");
				textRef.setReference(annotation.getWord());
				textRef.setRightText(" " + String.join(" ", text.subList(i + 1, text.size())));
				textualRefs.add(textRef);
			} 
		}
		
		return textualRefs;
	}
	
	private static List<List<Annotation>> getSentences(List<Annotation> annotations) {
		List<List<Annotation>> sentences = new ArrayList<>();
		
		List<Annotation> sentence = new ArrayList<>();
		
		for (Annotation annotation : annotations) {
			if (annotation.getStartsNewSentence()) {
				// first sentence
				if (!sentence.isEmpty()) sentences.add(sentence);
				sentence = new ArrayList<>();
				sentence.add(annotation);
			} else sentence.add(annotation); 
		}
		if (!sentence.isEmpty()) sentences.add(sentence);
		log.debug("sentences: ");
		for (List<Annotation> sent : sentences) log.debug("" + sent);;
		return sentences;
	}
	
	// for testing of link creation: 
	// 1) create links from these manually created textual references
	// 2) compare links to manually created list of links
	protected static List<TextualReference> toTextualReferenceList(List<Annotation> annotations, 
			Set<Metadata> relevantFields) {
		List<TextualReference> references = new ArrayList<>();
		
		List<List<Annotation>> sentences = getSentences(annotations);
		for (List<Annotation> sentence : sentences) {
				List<TextualReference> textRefs = createTextualReferencesFromAnnotations(sentence, relevantFields);
				if (!textRefs.isEmpty()) references.addAll(textRefs);
		}
		return references;
	}
	
	private Metadata[] buildMetadataCharArray(List<Annotation> annotations) {
		Metadata[] charAnnotations = new Metadata[
		                               annotations.get(annotations.size()-1).getCharEnd() + 1];
		for (Annotation annotation : annotations) {
			// Make sure annotations contain character offsets for this method to work
			if (annotation.getCharStart() == Integer.MIN_VALUE 
					|| annotation.getCharEnd() == Integer.MIN_VALUE)
				throw new IllegalArgumentException("Annotation missing character offsets, aborting.");
			
			for (int i = annotation.getCharStart(); i <= annotation.getCharEnd(); i++) {
				charAnnotations[i] = annotation.getMetadata();
			}
		}
		return charAnnotations;
	}
	
	private String reconstructText(List<Annotation> annotations) {
		String text = "";
		for (Annotation annotation : annotations) {
			text += " " + annotation.getWord();
		}
		return text;
	}
	
	private List<List<CoreLabel>> applyTokenizer(String text) {
		return TokenizerStanford.getInvertibleSentences(text, true, true);
	}
	
	private List<Annotation> transferAnnotationsToTokenizedText(Metadata[] charAnnotations, 
			List<List<CoreLabel>> sentences, String originalText) {
		List<Annotation> transformedAnnotations = new ArrayList<>();
		
		int position = -1;
		for (List<CoreLabel> sentence : sentences) {
			int wordInSentence = -1;
			for (CoreLabel token : sentence) {
				position ++;
				wordInSentence++;
				/*log.debug("originaltext: " + token.originalText());
				log.debug("tokenized: " + token.word());
				log.debug("token beginposition: " + token.beginPosition());
				log.debug("token endposition: " + token.endPosition());
				
				log.debug("annotation at beginpos: " + charAnnotations[token.beginPosition()]);
				log.debug("annotation at endpos: " + charAnnotations[token.endPosition() - 1]);
				*/
				
				// if this token was not separated from the previous token by whitespace 
				// in the original text, it means that the tokenizer split this word 
				boolean entitySplit = false;
				char prevChar = originalText.charAt(token.beginPosition() - 1);

				if (prevChar != ' ') {
					//original text was split here
					entitySplit = true;
				}
				
				String multiword = TokenizerStanford.splitCompounds(token.word());
					int w = -1;
					int curChar = token.beginPosition();
					for (String word : multiword.split(" ")) {
						w ++;
						Annotation anno = new Annotation();
						anno.setWord(word);
						if (!entitySplit && w == 0) anno.setMetadata(charAnnotations[token.beginPosition()]);
						// if this token used to be part of a larger word in the annotation file,
						// change the BIO annotation to _i
						else anno.setMetadata(getFollowingClass(charAnnotations[token.beginPosition()]));
						if (wordInSentence == 0 && w == 0) anno.setStartsNewSentence();
						anno.setPosition(position + w);
						
						// charStart and charEnd positions correspond to positions in 
						// the original text!
						anno.setCharStart(curChar);
						int wordLength = word.length();
						// special characters that may be inserted by tokenizer
						if (word.equals("-LRB-") 
								|| word.equals("-RRB-") 
								|| word.equals("*NL*")) 
							wordLength = 1;
						anno.setCharEnd(curChar + wordLength);
						transformedAnnotations.add(anno);
						curChar = curChar + word.length();
					}
			}
		}
		return transformedAnnotations;
	}
	
	// ignore annotations for punctuation
	// TODO test if this yields the same number of annotations...
	private List<Annotation> transferAnnotationsToTokenizedText2(Metadata[] charAnnotations, 
			List<List<CoreLabel>> sentences, String originalText) {
		List<Annotation> transformedAnnotations = new ArrayList<>();
		
		int position = -1;
		for (List<CoreLabel> sentence : sentences) {
			int wordInSentence = -1;
			boolean moveAnnotation = false;
			for (CoreLabel token : sentence) {
				position ++;
				wordInSentence++;
				
				// if this token was not separated from the previous token by whitespace 
				// in the original text, it means that the tokenizer split this word 
				boolean entitySplit = false;
				char prevChar = originalText.charAt(token.beginPosition() - 1);

				if (prevChar != ' ') {
					//original text was split here
					entitySplit = true;
				}
				
				String multiword = TokenizerStanford.splitCompounds(token.word());
				int w = -1;
				int curChar = token.beginPosition();
				
				for (String word : multiword.split(" ")) {
					w ++;
					Annotation anno = new Annotation();
					anno.setWord(word);
					
					// word is punctuation, ignore annotated label
					// TODO may punctuation occur inside of an annotated entity?
					if (word.equals("-LRB-") || word.equals("-RRB-") || 
							word.equals("``") || word.equals("''") |
							word.matches("[\\p{Punct}\\p{P}]+")) {
						
						anno.setMetadata(Metadata.none);
						if (charAnnotations[token.beginPosition() + 1].toString().endsWith("_i") && 
								charAnnotations[token.beginPosition()].toString().endsWith("_i")) {
							anno.setMetadata(charAnnotations[token.beginPosition()]);
						}
						else if (charAnnotations[token.beginPosition()].toString().endsWith("_b")) {
							moveAnnotation = true;
						}
					} else {
					
						if (!entitySplit && w == 0) {
							if (moveAnnotation) {
								anno.setMetadata(getStartingClass(charAnnotations[token.beginPosition()]));
								moveAnnotation = false;
							} else anno.setMetadata(charAnnotations[token.beginPosition()]);
						}
						else if (entitySplit && (w == 0) ) {
							if (moveAnnotation) {
								anno.setMetadata(getStartingClass(charAnnotations[token.beginPosition()]));
								moveAnnotation = false;
							} else	anno.setMetadata(getFollowingClass(charAnnotations[token.beginPosition()]));	
						}
						// if this token used to be part of a larger word in the annotation file,
						// change the BIO annotation to _i
						else {
							if (moveAnnotation) {
								anno.setMetadata(getStartingClass(charAnnotations[token.beginPosition()]));
								moveAnnotation = false;
							}
							else anno.setMetadata(getFollowingClass(charAnnotations[token.beginPosition()]));
						}
					}
					if (wordInSentence == 0 && w == 0) anno.setStartsNewSentence();
					anno.setPosition(position + w);
						
					// charStart and charEnd positions correspond to positions in 
					// the original text!
					anno.setCharStart(curChar);
					int wordLength = word.length();
					// special characters that may be inserted by tokenizer
					if (word.equals("-LRB-") 
							|| word.equals("-RRB-") 
							|| word.equals("*NL*")) 
						wordLength = 1;
					anno.setCharEnd(curChar + wordLength);
					transformedAnnotations.add(anno);
					curChar = curChar + word.length();
				}
			}
		}
		return transformedAnnotations;
	}
	
	private Metadata getFollowingClass(Metadata metadata) {
		return Enum.valueOf(Annotation.Metadata.class, metadata.toString().replace("_b", "_i"));
	}
	
	private Metadata getStartingClass(Metadata metadata) {
		return Enum.valueOf(Annotation.Metadata.class, metadata.toString().replace("_i", "_b"));
	}
	
	// TODO annotations must be tokenized in same way as textual references...
	/**
	 * Transform annotations: apply tokenizer to text but keep annotations. 
	 * 
	 * @param annotations
	 */
	public List<Annotation> tokenizeAnnotations(List<Annotation> annotations) throws IllegalArgumentException {
		Metadata[] charAnnotations = buildMetadataCharArray(annotations);
		log.debug(String.format("charAnnotation array contains annotations for %s chars", charAnnotations.length));
		String originalText = reconstructText(annotations);
		List<List<CoreLabel>> sentences = applyTokenizer(originalText);
		log.debug(String.format("split annotated text into %s sentences", sentences.size()));
		List<Annotation> transformedAnnotations = transferAnnotationsToTokenizedText2(charAnnotations, 
				sentences, originalText);
		return transformedAnnotations;
	}
	
	
	protected List<Annotation> importData(String filename) throws IOException {
		String input = read(filename);
		log.debug("read annotation file " + filename);
		return parse(input);
	}
	
	// title_b, title_i and title have equal classes...
	private static boolean metadataClassesEqual(Metadata metadata1, Metadata metadata2) {
		if (metadata1.equals(metadata2)) return true;
		else if (metadata1.toString().replaceAll("_\\w", "")
				.equals(metadata2.toString().replaceAll("_\\w", "")))
			return true;
		else return false;
	}
	
	private static boolean metadataClassesFollow(Metadata metadata1, Metadata metadata2) {
		if (metadata1.toString().endsWith("_b") &&
				metadata2.toString().endsWith("_i") &&
				metadata1.toString().replaceAll("_\\w", "")
				.equals(metadata2.toString().replaceAll("_\\w", "")))
			return true;
		else if (metadata1.toString().endsWith("_i") &&
				metadata2.toString().endsWith("_i") &&
				metadata1.toString().replaceAll("_\\w", "")
				.equals(metadata2.toString().replaceAll("_\\w", "")))
			return true;
		else return false;
	}
	
	/**
	 * Words are annotated using the BIO system; this method merges 
	 * words that are annotated as being one entity.
	 * 
	 * @param annotations
	 */
	public static List<Annotation> mergeNgrams(List<Annotation> annotations) {
		List<Annotation> mergedAnnotations = new ArrayList<>();
		for (int i = 0; i < annotations.size(); i++) {
			Annotation anno = annotations.get(i);

			if (anno.getMetadata().toString().endsWith("_b")) {
				Metadata meta = anno.getMetadata();
				StringJoiner ngram = new StringJoiner(" ", "", "");
				ngram.add(anno.getWord());
				int charEnd = anno.getCharEnd();
				for (int j = i+1; j < annotations.size(); j++) {
					Annotation nextAnno = annotations.get(j);
					if (metadataClassesFollow(meta, nextAnno.getMetadata())) {
						ngram.add(nextAnno.getWord());
						charEnd = nextAnno.getCharEnd();
					}
					else {
						Annotation mergedAnnotation = new Annotation(anno);
						mergedAnnotation.setCharEnd(charEnd);
						mergedAnnotation.setWord(ngram.toString());
						mergedAnnotations.add(mergedAnnotation);
						i = j-1;
						break;
					}
				}	
			}
			else mergedAnnotations.add(anno);
		}
		return mergedAnnotations;
	}
	
	
	
	protected static void writeToAnnotatedTextRefFile(File outFile, 
			List<TextualReference> references, String label) throws IOException {
		FileUtils.write(outFile, "", false);
		for (TextualReference textRef : references)
			FileUtils.write(outFile, textRef.toPrettyString()
					.replace("**[", "*" + label.replace("_b", "").toUpperCase() 
							+ "*[") + "\n", true);
	}
	
	protected static List<String> toAnnotatedTextRefs(List<TextualReference> references, 
			String label) {
		List<String> annotatedTextRefs = new ArrayList<>();
		for (TextualReference textRef : references)
			annotatedTextRefs.add(textRef.toPrettyString()
					.replace("**[", "*" + label.replace("_b", "").toUpperCase() 
							+ "*["));
		return annotatedTextRefs;
	}
	
	private static String removeAnnotations(String annotatedTextualRef) {
		return annotatedTextualRef
				.replaceAll("\\*.*\\*\\[\\s+", "")
				.replaceAll("\\s+\\]\\*\\*", "")
				.trim();
	}
	
	// return entity, label, annotatedEntity, position
	private static String[] getAnnotatedEntity(String textRef) {
		String[] annotatedEntity = new String[4];
		Matcher annotatedEntityMatcher = annotatedEntityPat.matcher(textRef);
		annotatedEntityMatcher.find();
		annotatedEntity[1] = annotatedEntityMatcher.group(1);
		annotatedEntity[0] = annotatedEntityMatcher.group(2);
		annotatedEntity[2] = annotatedEntityMatcher.group();
		annotatedEntity[3] = String.valueOf(annotatedEntityMatcher.start());
		return annotatedEntity;
	}
	
	private static String addTextRefAnnotation(String toAdd, String target) {
		// 1. find annotated entity in toAdd
		// 2. find annotated entity in target
		// 3. replace entity in target with entity in toAdd
		log.trace(String.format("adding annotation in '%s' to '%s'", toAdd, target));
		String[] annoToAdd = getAnnotatedEntity(toAdd);
		// entity may occur multiple times in target. Replace only the annotated entity in toAdd
		// need to consider the additional characters in the merged annotations!
		// TODO this is only a work-around. In usual cases, this may annotate more 
		// entities than are annotated originally
		return target.substring(0, Integer.valueOf(annoToAdd[3]))
				+ (target.substring(Integer.valueOf(annoToAdd[3]), target.length())
					.replaceAll("(?<!\\*" + annoToAdd[1] + "\\*\\[  )" + Pattern.quote(annoToAdd[0]), annoToAdd[2]));
	}
	
	protected static Collection<String> mergeAnnotatedTextualReferences(
			List<String> annotatedTextRefs) {
		Map<String, String> mergedTextRefs = new HashMap<>();
		for (String annotatedTextRef : annotatedTextRefs) {
			String textRefText = removeAnnotations(annotatedTextRef);
			String mergedRef = mergedTextRefs.getOrDefault(
					textRefText, annotatedTextRef);
			if (!mergedRef.equals(annotatedTextRef)) {
				mergedRef = addTextRefAnnotation(annotatedTextRef, mergedRef);
			}
			mergedTextRefs.put(textRefText, mergedRef);
		}
		return mergedTextRefs.values();
	}
	
	//TODO implement (separate class)
	// agreement scores: matches and overlaps of annotated items; classifications...
	/*
	protected void calculateAgreementScores(List<Annotation> annotations1, List<Annotation> annotations2) {
		//for (Annotation anno : annotations1)
		return;
	}*/

}