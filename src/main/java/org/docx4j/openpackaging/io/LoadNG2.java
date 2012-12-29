/*
 *  Copyright 2007-2012, Plutext Pty Ltd.
 *   
 *  This file is part of docx4j.

    docx4j is licensed under the Apache License, Version 2.0 (the "License"); 
    you may not use this file except in compliance with the License. 

    You may obtain a copy of the License at 

        http://www.apache.org/licenses/LICENSE-2.0 

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
    See the License for the specific language governing permissions and 
    limitations under the License.

 */

package org.docx4j.openpackaging.io;



import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.model.datastorage.CustomXmlDataStorage;
import org.docx4j.openpackaging.Base;
import org.docx4j.openpackaging.URIHelper;
import org.docx4j.openpackaging.contenttype.ContentType;
import org.docx4j.openpackaging.contenttype.ContentTypeManager;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.exceptions.PartUnrecognisedException;
import org.docx4j.openpackaging.packages.OpcPackage;
import org.docx4j.openpackaging.parts.DefaultXmlPart;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.XmlPart;
import org.docx4j.openpackaging.parts.WordprocessingML.BibliographyPart;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPart;
import org.docx4j.openpackaging.parts.opendope.ComponentsPart;
import org.docx4j.openpackaging.parts.opendope.ConditionsPart;
import org.docx4j.openpackaging.parts.opendope.QuestionsPart;
import org.docx4j.openpackaging.parts.opendope.StandardisedAnswersPart;
import org.docx4j.openpackaging.parts.opendope.XPathsPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;


/**
 * Create a Package object using a PartLoader.
 * 
 * This class doesn't care how the parts are physically
 * stored (that is PartLoader's problem).  
 * 
 * What this class knows how to do is to traverse the
 * opc, via its relationships.
 * 
 * @author jharrop
 * 
 */
public class LoadNG2 extends Load {
		
	private static Logger log = Logger.getLogger(LoadNG2.class);


	private PartStore partLoader;	
	
	public LoadNG2(PartStore partLoader) {
		this.partLoader = partLoader;
	}

	public LoadNG2() {
		throw new RuntimeException();
	}
	
//	public OpcPackage get(String filepath) throws Docx4JException {
//		return get(new File(filepath));
//	}
//	
//	
//	public OpcPackage get(File f) throws Docx4JException {
//		log.info("Filepath = " + f.getPath() );
//		
//		partLoader = new ZipPartLoader(f);
//		
//		return process();
//	}
//
//	public OpcPackage get(InputStream is) throws Docx4JException {
//
//		partLoader = new ZipPartLoader(is);
//	            
//		// At this point, we're finished with the zip input stream
//        // TODO, so many of the below methods could be renamed.
//        // If performance is ok, LoadFromJCR could be refactored to
//        // work the same way
//		
//		return process();
//	}
	
	public OpcPackage get() throws Docx4JException {

		// 1. Get [Content_Types].xml
		ContentTypeManager ctm = new ContentTypeManager();
		try {
			InputStream is = partLoader.getInputStreamForPart("[Content_Types].xml");		
			ctm.parseContentTypesFile(is);
		} catch (IOException e) {
			throw new Docx4JException("Couldn't get [Content_Types].xml from ZipFile", e);
		} catch (NullPointerException e) {
			throw new Docx4JException("Couldn't get [Content_Types].xml from ZipFile", e);
		}

		// 2. Create a new Package
		//		Eventually, you'll also be able to create an Excel package etc
		//		but only the WordML package exists at present
		OpcPackage p = ctm.createPackage();

		// 3. Start with _rels/.rels

//		<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
//		  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
//		  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
//		  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
//		</Relationships>		
		
		String partName = "_rels/.rels";
		RelationshipsPart rp = getRelationshipsPartFromZip(p, partName);		
		p.setRelationships(rp);
		
		log.debug( "Object created for: " + partName);
		
		// 5. Now recursively 
//		(i) create new Parts for each thing listed
//		in the relationships
//		(ii) add the new Part to the package
//		(iii) cross the PartName off unusedZipEntries
		addPartsFromRelationships(p, rp, ctm );

		// 6.
		registerCustomXmlDataStorageParts(p);
		 
		 return p;
	}
	
	private RelationshipsPart getRelationshipsPartFromZip(Base p, String partName) 
			throws Docx4JException {
		
		
		RelationshipsPart rp = null;
		
		InputStream is = null;
		try {
			is =  partLoader.getInputStreamForPart( partName);
			//thePart = new RelationshipsPart( p, new PartName("/" + partName), is );
			rp = new RelationshipsPart(new PartName("/" + partName) );
			rp.setSourceP(p);
			rp.unmarshal(is);
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new Docx4JException("Error getting document from Zipped Part:" + partName, e);
			
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException exc) {
					exc.printStackTrace();
				}
			}
		}
		
		return rp;
	// debugPrint(contents);
	// TODO - why don't any of the part names in this document start with "/"?
	}
	
		
	/* recursively 
	(i) create new Parts for each thing listed
	in the relationships
	(ii) add the new Part to the package
	(iii) cross the PartName off unusedZipEntries
	*/
	private void addPartsFromRelationships( 
			Base source, RelationshipsPart rp, ContentTypeManager ctm)
		throws Docx4JException {
		
		OpcPackage pkg = source.getPackage();				
		
		for ( Relationship r : rp.getRelationships().getRelationship() ) {
			
			log.debug("\n For Relationship Id=" + r.getId() 
					+ " Source is " + rp.getSourceP().getPartName() 
					+ ", Target is " + r.getTarget()
					+ ", type: " + r.getType() );
					
				// This is usually the first logged comment for
				// a part, so start with a line break.
			try {				
				getPart(pkg, rp, r, ctm);
			} catch (Exception e) {
				throw new Docx4JException("Failed to add parts from relationships", e);
			}
		}
		
		
	}

	/**
	 * Get a Part (except a relationships part), and all its related parts.  
	 * This can be called directly from outside the library, in which case 
	 * the Part will not be owned by a Package until the calling code makes it so.  
	 * 
	 * @param zf
	 * @param source
	 * @param unusedZipEntries
	 * @param pkg
	 * @param r
	 * @param resolvedPartUri
	 * @throws Docx4JException
	 * @throws InvalidFormatException
	 */
	private void getPart(OpcPackage pkg, RelationshipsPart rp, 
			Relationship r, ContentTypeManager ctm)
			throws Docx4JException, InvalidFormatException, URISyntaxException {
		
		Base source = null;
		String resolvedPartUri = null;
		
		if (r.getType().equals(Namespaces.HYPERLINK)) {
			// Could be Internal or External
			// Example of Internal is w:drawing/wp:inline/wp:docPr/a:hlinkClick
			log.info("Encountered (but not loading) hyperlink " + r.getTarget()  );				
			return;			
		} else 
			if (r.getTargetMode() == null
				|| !r.getTargetMode().equals("External") ) {
			
			// Usual case
			
			source = rp.getSourceP();
			resolvedPartUri = URIHelper.resolvePartUri(rp.getSourceURI(), new URI(r.getTarget() ) ).toString();		

			// Now drop leading "/'
			resolvedPartUri = resolvedPartUri.substring(1);				

			// Now normalise it .. ie abc/def/../ghi
			// becomes abc/ghi
			// Maybe this isn't necessary with a zip file,
			// - ZipFile class may be smart enough to do it.
			// But it is certainly necessary in the JCR case.
//			resolvedPartUri = (new java.net.URI(resolvedPartUri)).normalize().toString();
//			log.info("Normalised, it is " + resolvedPartUri );				
			
		} else {			
			// EXTERNAL			
			if (loadExternalTargets && 
					r.getType().equals( Namespaces.IMAGE ) ) {
					// It could instead be, for example, of type hyperlink,
					// and we don't want to try to fetch that
				log.info("Loading external resource " + r.getTarget() 
						   + " of type " + r.getType() );
				BinaryPart bp = ExternalResourceUtils.getExternalResource(r.getTarget());
				pkg.getExternalResources().put(bp.getExternalTarget(), bp);			
			} else {				
				log.info("Encountered (but not loading) external resource " + r.getTarget() 
						   + " of type " + r.getType() );				
			}						
			return;
		}
		
		
		String relationshipType = r.getType();		
		Part part;
		
		if (pkg.handled.get(resolvedPartUri)!=null) {
			
			// The source Part (or Package) might have a convenience
			// method for this
			part = pkg.getParts().getParts().get(new PartName("/" + resolvedPartUri));
			if (source.setPartShortcut(part, relationshipType ) ) {
				log.debug("Convenience method established from " + source.getPartName() 
						+ " to " + part.getPartName());
			}			
			return;
		}
		
		part = getRawPart(ctm, resolvedPartUri, r); // will throw exception if null

		// The source Part (or Package) might have a convenience
		// method for this
		if (source.setPartShortcut(part, relationshipType ) ) {
			log.debug("Convenience method established from " + source.getPartName() 
					+ " to " + part.getPartName());
		}

		
		if (part instanceof BinaryPart
				|| part instanceof DefaultXmlPart) {
			// The constructors of other parts should take care of this...
			part.setRelationshipType(relationshipType);
		}
		rp.loadPart(part, r);
		pkg.handled.put(resolvedPartUri, resolvedPartUri);

		
//		unusedZipEntries.put(resolvedPartUri, new Boolean(false));
		
		RelationshipsPart rrp = getRelationshipsPart(part);
		if (rrp!=null) {
			// recurse via this parts relationships, if it has any
			addPartsFromRelationships(part, rrp, ctm );
			String relPart = PartName.getRelationshipsPartName(
					part.getPartName().getName().substring(1) );
//			unusedZipEntries.put(relPart, new Boolean(false));					
		}
	}

	/**
	 * Get the Relationships Part (if there is one) for a given Part.  
	 * Otherwise return null.
	 * 
	 * @param zf
	 * @param part
	 * @return
	 * @throws InvalidFormatException
	 */
	public RelationshipsPart getRelationshipsPart(Part part)
	throws Docx4JException, InvalidFormatException {
		
		RelationshipsPart rrp = null;
		// recurse via this parts relationships, if it has any
		//String relPart = PartName.getRelationshipsPartName(target);
		String relPart = PartName.getRelationshipsPartName(
				part.getPartName().getName().substring(1) );
		
		if (partLoader.partExists(relPart)) {
		//if (partByteArrays.get(relPart) !=null ) {
			log.debug("Found relationships " + relPart );
			rrp = getRelationshipsPartFromZip(part,  relPart);
			part.setRelationships(rrp);
		} else {
			log.debug("No relationships " + relPart );	
			return null;
		}
		return rrp;
	}
	
	

	/**
	 * Get a Part (except a relationships part), but not its relationships part
	 * or related parts.  Useful if you need quick access to just this part.
	 * This can be called directly from outside the library, in which case 
	 * the Part will not be owned by a Package until the calling code makes it so.  
	 * @see  To get a Part and all its related parts, and add all to a package, use
	 * getPart.
	 * @param partByteArrays
	 * @param ctm
	 * @param resolvedPartUri
	 * @param rel
	 * @return
	 * @throws Docx4JException including if result is null
	 */
	public Part getRawPart(
			ContentTypeManager ctm, String resolvedPartUri, Relationship rel)	
			throws Docx4JException {
		
		Part part = null;
		
		InputStream is = null;
		try {
			try {
				log.debug("resolved uri: " + resolvedPartUri);
				is = partLoader.getInputStreamForPart( resolvedPartUri);
				
				// Get a subclass of Part appropriate for this content type	
				// This will throw UnrecognisedPartException in the absence of
				// specific knowledge. Hence it is important to get the is
				// first, as we do above.
				part = ctm.getPart("/" + resolvedPartUri, rel);				

				log.info("ctm returned " + part.getClass().getName() );
				
				if (part instanceof org.docx4j.openpackaging.parts.ThemePart) {

					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcThemePart);
					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
					
				} else if (part instanceof org.docx4j.openpackaging.parts.DocPropsCorePart ) {

						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcDocPropsCore);
						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
						
				} else if (part instanceof org.docx4j.openpackaging.parts.DocPropsCustomPart ) {

						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcDocPropsCustom);
						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
						
				} else if (part instanceof org.docx4j.openpackaging.parts.DocPropsExtendedPart ) {

						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcDocPropsExtended);
						((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
					
				} else if (part instanceof org.docx4j.openpackaging.parts.CustomXmlDataStoragePropertiesPart ) {

					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcCustomXmlProperties);
					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );

				} else if (part instanceof org.docx4j.openpackaging.parts.digitalsignature.XmlSignaturePart ) {

					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jcXmlDSig);
					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
					
				} else if (part instanceof org.docx4j.openpackaging.parts.JaxbXmlPart) {

					// MainDocument part, Styles part, Font part etc
					
					//((org.docx4j.openpackaging.parts.JaxbXmlPart)part).setJAXBContext(Context.jc);
					((org.docx4j.openpackaging.parts.JaxbXmlPart)part).unmarshal( is );
					
				} else if (part instanceof org.docx4j.openpackaging.parts.WordprocessingML.BinaryPart) {
					
					log.debug("Detected BinaryPart " + part.getClass().getName() );
					((BinaryPart)part).setBinaryData(is);

				} else if (part instanceof org.docx4j.openpackaging.parts.CustomXmlDataStoragePart ) {
					
					// Is it a part we know?
					try {
						Unmarshaller u = Context.jc.createUnmarshaller();
						Object o = u.unmarshal( is );						
						log.debug(o.getClass().getName());
						
						PartName name = part.getPartName();
						
						if (o instanceof org.opendope.conditions.Conditions) {
							
							part = new ConditionsPart(name);
							((ConditionsPart)part).setJaxbElement(
									(org.opendope.conditions.Conditions)o);
							
							
						} else if (o instanceof org.opendope.xpaths.Xpaths) {
							
							part = new XPathsPart(name);
							((XPathsPart)part).setJaxbElement(
									(org.opendope.xpaths.Xpaths)o);
														
						} else if (o instanceof org.opendope.questions.Questionnaire) {
							
							part = new QuestionsPart(name);
							((QuestionsPart)part).setJaxbElement(
									(org.opendope.questions.Questionnaire)o);
							
						} else if (o instanceof org.opendope.answers.Answers) {
							
							part = new StandardisedAnswersPart(name);
							((StandardisedAnswersPart)part).setJaxbElement(
									(org.opendope.answers.Answers)o);
							
						} else if (o instanceof org.opendope.components.Components) {
							
							part = new ComponentsPart(name);
							((ComponentsPart)part).setJaxbElement(
									(org.opendope.components.Components)o);

						} else if (o instanceof JAXBElement<?> 
								&& XmlUtils.unwrap(o) instanceof org.docx4j.bibliography.CTSources) {
							part = new BibliographyPart(name);
							((BibliographyPart)part).setJaxbElement(
									(JAXBElement<org.docx4j.bibliography.CTSources>)o);
														
						} else {
							
							log.error("TODO: handle known CustomXmlPart part  " + o.getClass().getName());

							CustomXmlDataStorage data = getCustomXmlDataStorageClass().factory();					
							is.reset();
							data.setDocument(is); // Not necessarily JAXB, that's just our method name
							((org.docx4j.openpackaging.parts.CustomXmlDataStoragePart)part).setData(data);						
							
						}
						
					} catch (javax.xml.bind.UnmarshalException ue) {

						log.warn("No JAXB model for this CustomXmlDataStorage part; " + ue.getMessage()  );
						
						CustomXmlDataStorage data = getCustomXmlDataStorageClass().factory();	
						is.reset();
						data.setDocument(is); // Not necessarily JAXB, that's just our method name
						((org.docx4j.openpackaging.parts.CustomXmlDataStoragePart)part).setData(data);						
					}					

				} else if (part instanceof org.docx4j.openpackaging.parts.XmlPart ) {
					
//					try {
						((XmlPart)part).setDocument(is);
						
					// Experimental 22/6/2011; don't fall back to binary (which we used to) 
						
//					} catch (Docx4JException d) {
//						// This isn't an XML part after all,
//						// even though ContentTypeManager detected it as such
//						// So get it as a binary part
//						part = getBinaryPart(partByteArrays, ctm, resolvedPartUri);
//						log.warn("Could not parse as XML, so using BinaryPart for " 
//								+ resolvedPartUri);						
//						((BinaryPart)part).setBinaryData(is);
//					}
					
				} else {
					// Shouldn't happen, since ContentTypeManagerImpl should
					// return an instance of one of the above, or throw an
					// Exception.
					
					log.error("No suitable part found for: " + resolvedPartUri);
					part = null;					
				}
			
			} catch (PartUnrecognisedException e) {
				log.error("PartUnrecognisedException shouldn't happen anymore!", e);
				// Try to get it as a binary part
				part = getBinaryPart(ctm, resolvedPartUri);
				log.warn("Using BinaryPart for " + resolvedPartUri);
				
				((BinaryPart)part).setBinaryData(is);
			}
		} catch (Exception ex) {
			// IOException, URISyntaxException
			ex.printStackTrace();
			throw new Docx4JException("Failed to getPart", ex);			
			
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException exc) {
					exc.printStackTrace();
				}
			}
		}
		
        if (part == null) {
            throw new Docx4JException("cannot find part " + resolvedPartUri + " from rel "+ rel.getId() + "=" + rel.getTarget());
        }
		
		return part;
	}
	
	public Part getBinaryPart(
			ContentTypeManager ctm, String resolvedPartUri)	
			throws Docx4JException {

		Part part = null;
		InputStream in = null;					
		try {
			in = partLoader.getInputStreamForPart(resolvedPartUri);
			//in = partByteArrays.get(resolvedPartUri).getInputStream();
			part = new BinaryPart( new PartName("/" + resolvedPartUri));
			
			// Set content type
			part.setContentType(
					new ContentType(
							ctm.getContentType(new PartName("/" + resolvedPartUri)) ) );
			
			((BinaryPart)part).setBinaryData(in);
			log.info("Stored as BinaryData" );
			
		} catch (Exception ioe) {
			ioe.printStackTrace() ;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException exc) {
					exc.printStackTrace();
				}
			}
		}
		return part;
	}	

	// Testing
	public static void main(String[] args) throws Exception {
		String filepath = System.getProperty("user.dir") + "/sample-docs/word/FontEmbedded.docx";
		log.info("Path: " + filepath );
		ZipPartStore partLoader = new ZipPartStore(new File(filepath));
		LoadNG2 loader = new LoadNG2(partLoader);
		loader.get();		
	}
	
}