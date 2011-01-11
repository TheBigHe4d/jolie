package joliex.wsdl;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import com.ibm.wsdl.PortTypeImpl;
import com.ibm.wsdl.ServiceImpl;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.wsdl.Binding;
import javax.wsdl.BindingFault;
import javax.wsdl.BindingInput;
import javax.wsdl.BindingOperation;
import javax.wsdl.BindingOutput;
import javax.wsdl.Definition;
import javax.wsdl.Fault;
import javax.wsdl.Input;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.OperationType;
import javax.wsdl.Output;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.Service;
import javax.wsdl.Types;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap.SOAPBody;
import javax.wsdl.extensions.soap.SOAPFault;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLWriter;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import jolie.lang.NativeType;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.OneWayOperationDeclaration;
import jolie.lang.parse.ast.OperationDeclaration;
import jolie.lang.parse.ast.RequestResponseOperationDeclaration;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.ast.types.TypeDefinitionLink;
import jolie.lang.parse.ast.types.TypeInlineDefinition;
import jolie.lang.parse.util.ProgramInspector;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author Francesco Bullini and Claudio Guidi
 */
public class WSDLDocCreator
{
        // Schema
        private Document schemaDocument;
        private Element schemaRootElement ;
        private int MAX_CARD = 2147483647;
        private String tns;
        private String tns_schema;

	static ExtensionRegistry extensionRegistry;
	private static WSDLFactory wsdlFactory;
	private Definition localDef = null;
	private WSDLWriter ww = null;
	private Writer fw = null;
        private List<String> rootTypes = new ArrayList<String>();
        private ProgramInspector inspector;
	private URI originalFile;


	public WSDLDocCreator( ProgramInspector inspector, URI originalFile )
	{
		
                this.inspector=inspector;
		this.originalFile=originalFile;

	}

	public Definition initWsdl( String serviceName, String filename )
	{
		try {
			wsdlFactory = WSDLFactory.newInstance();
			localDef = wsdlFactory.newDefinition();
			extensionRegistry = wsdlFactory.newPopulatedExtensionRegistry();
			if ( serviceName != null ) {
				QName servDefQN = new QName( serviceName );
				localDef.setQName( servDefQN );
			}
			localDef.addNamespace( NameSpacesEnum.WSDL.getNameSpacePrefix(), NameSpacesEnum.WSDL.getNameSpaceURI() );
			localDef.addNamespace( NameSpacesEnum.SOAP.getNameSpacePrefix(), NameSpacesEnum.SOAP.getNameSpaceURI() );
			localDef.addNamespace( "tns", tns );
			localDef.addNamespace( NameSpacesEnum.XML_SCH.getNameSpacePrefix(), NameSpacesEnum.XML_SCH.getNameSpaceURI() );
			localDef.setTargetNamespace( tns );
                        localDef.addNamespace( "xsd1", tns_schema );
			fw = new FileWriter( filename  );
		} catch( IOException ex ) {
			Logger.getLogger( WSDLDocCreator.class.getName() ).log( Level.SEVERE, null, ex );
		} catch( WSDLException ex ) {
			Logger.getLogger( WSDLDocCreator.class.getName() ).log( Level.SEVERE, null, ex );
		}

		return localDef;
	}


	
	public void ConvertDocument( String filename, String tns ) throws Jolie2WsdlException
	{

                boolean soap_port_flag = false;
                System.out.println( "Starting conversion..." );

                this.tns = tns + ".wsdl";
                this.tns_schema = tns + ".xsd";

		try {
			initWsdl( null, filename );
			
			schemaDocument = this.createDOMdocument();
			schemaRootElement = this.createSchemaRootElement( schemaDocument );
			

                        // scans inputPorts
                        InputPortInfo[] inputPortList = inspector.getInputPorts( originalFile );


                        
			for( InputPortInfo inputPort : inputPortList ) { 
                                        
                                if ( inputPort.protocolId().equals(  "soap"  ) )  {
                                    soap_port_flag = true;
                                

                                    // portType creation
                                    String portTypeName = inputPort.id();
                                    PortType pt = createPortType(localDef, portTypeName);

                                    // binding creation
                                    Binding bd = createBindingSOAP(localDef, pt, portTypeName + "SOAPBinding" );

                                    // service creation
                                    String address = inputPort.location().toString().substring( 6 );   // exclude socket word
                                    address = "http" + address;
                                    createService(localDef, portTypeName + "Service", bd, address );

                                     // scans interfaces
                                    for ( InterfaceDefinition interfaceDefinition:inputPort.getInterfaceList() )
                                    {
                                             // scan operations
                                            Map< String , OperationDeclaration > operationMap = interfaceDefinition.operationsMap();

                                            for ( Entry< String , OperationDeclaration > operationEntry:operationMap.entrySet() ) {
                                                    if ( operationEntry.getValue() instanceof OneWayOperationDeclaration ) {
                                                            // OW

                                                            //-------------- adding operation
                                                            OneWayOperationDeclaration oneWayOperation = ( OneWayOperationDeclaration)operationEntry.getValue();
                                                            Operation wsdlOp = addOWOperation2PT( localDef, pt, oneWayOperation  );

                                                             // adding operation binding
                                                             addOperationSOAPBinding( localDef, pt, wsdlOp, bd );

                                                    } else {
                                                            // RR
                                                            //-------------- adding operation
                                                           RequestResponseOperationDeclaration requestResponseOperation = ( RequestResponseOperationDeclaration ) operationEntry.getValue();
                                                           Operation wsdlOp = addRROperation2PT( localDef, pt, requestResponseOperation );

                                                           // adding operation binding
                                                           addOperationSOAPBinding( localDef, pt, wsdlOp, bd );
                                                    }

                                            }

                                    }
                                }
			}

                        if ( soap_port_flag == false ) {
                             throw( new Jolie2WsdlException( "ERROR: jolie2wsdl only support soap port declarations in jolie files" ) );
                        }
		
			setSchemaDocIntoWSDLTypes( schemaDocument );		
			WSDLWriter wsdl_writer = wsdlFactory.newWSDLWriter();
			wsdl_writer.writeWSDL( localDef, fw );
		} 
		catch( WSDLException ex ) {
			System.err.println( ex.getMessage() );
			Logger.getLogger( WSDLDocCreator.class.getName() ).log( Level.SEVERE, null, ex );
		} catch( Exception ex ) {
			System.err.println( ex.getMessage() );
			Logger.getLogger( WSDLDocCreator.class.getName() ).log( Level.SEVERE, null, ex );
		}
		System.out.println( "Success: WSDL document generated!" );
	}


        private String getSchemaNativeType ( NativeType nType ) {

            /*
             * TO DO:
             * wsdl_types ANY, RAW, VOID
             */
            String prefix = "xs:";
            String suffix = "";
             if ( nType.equals( NativeType.STRING)) {
                suffix =  "string";
            } else if ( nType.equals( NativeType.DOUBLE )) {
                suffix =  "double";
            } else if ( nType.equals( NativeType.INT )) {
                suffix =  "int";
            }
            if ( suffix.isEmpty() ) {
                return "";
            } else {
                return prefix+suffix;
            }
        }

        private Element createSubType( TypeDefinition type ) {
            if ( type instanceof TypeInlineDefinition ) {
                return createTypeDefinition( ( TypeInlineDefinition ) type, false, "" ); //typename is not relevant because it is not a root type
            } else {
                return createTypeDefinitionLink( ( TypeDefinitionLink ) type, false, "" ); //typename is not relevant because it is not a root type
            }
        }

        private Element createTypeDefinition( TypeInlineDefinition type, boolean root, String tName ) {
            
            Element newEl = schemaDocument.createElement( "xs:element" );
            String typename = type.id();
            if ( root == false ) {
                // set cardinality only if it is not a root element
                newEl.setAttribute( "minOccurs", new Integer( type.cardinality().min()).toString()  );
                String maxOccurs = "unbounded";
                if ( type.cardinality().max() < MAX_CARD  ) {
                 maxOccurs = new Integer( type.cardinality().max() ).toString();
                }
                newEl.setAttribute( "maxOccurs", maxOccurs );
            } else {
                // the name of the root type must be the same of the operation (contained into the parameter typename) in order to be conformant to SOAP protocol
                typename = tName;
            }
            newEl.setAttribute( "name", typename );
            String schemaType = getSchemaNativeType( type.nativeType() );
            if ( !schemaType.isEmpty() ) {
                newEl.setAttribute( "type", schemaType  );
            }
            // adding subtypes
            if ( type.hasSubTypes() ) {
                Element cpx = schemaDocument.createElement("xs:complexType");
                Element all = schemaDocument.createElement("xs:sequence");
                Iterator it = type.subTypes().iterator();
                while ( it.hasNext() ) {
                    all.appendChild( createSubType( ((Entry<String,TypeDefinition>) it.next()).getValue() ) );
                }
                cpx.appendChild( all );
                newEl.appendChild( cpx );
            }
            return newEl;
        }

         private Element createTypeDefinitionLink( TypeDefinitionLink type, boolean root, String tName ) {

            Element newEl = schemaDocument.createElement( "xs:element" );
            String typename = type.id();
            if ( root == false ) {
                // set cardinality only if it is not a root element
                newEl.setAttribute( "minOccurs", new Integer( type.cardinality().min()).toString()  );
                String maxOccurs = "unbounded";
                if ( type.cardinality().max() < MAX_CARD  ) {
                    maxOccurs = new Integer( type.cardinality().max() ).toString();
                }
                newEl.setAttribute( "maxOccurs", maxOccurs );
            } else {
                // the name of the root type must be the same of the operation (contained into the parameter typename) in order to be conformant to SOAP protocol
                typename = tName;
            }
            if ( !rootTypes.contains( type.linkedTypeName() )) {
                // add linked type to root if it does not contain them
                 if ( type.linkedType() instanceof TypeInlineDefinition ) {
                    schemaRootElement.appendChild( createTypeDefinition( ( TypeInlineDefinition ) type.linkedType(), true, type.linkedTypeName() ));
                } else if ( type.linkedType() instanceof TypeDefinitionLink ) {
                    schemaRootElement.appendChild( createTypeDefinitionLink( ( TypeDefinitionLink ) type.linkedType(), true, type.linkedTypeName() ));
                }
                rootTypes.add( type.linkedTypeName() ); 
            }
	    newEl.setAttribute( "name", typename );
            String schemaType = "tns:" + type.linkedTypeName();
            newEl.setAttribute( "type", schemaType  );
            
            return newEl;
        }

        
        private void addRootType( TypeDefinition rootType, String typename ) {

            if ( !rootTypes.contains( rootType.id() )) {
                if ( rootType instanceof TypeInlineDefinition ) {
                    schemaRootElement.appendChild( createTypeDefinition( ( TypeInlineDefinition ) rootType, true, typename  ));
                } else if ( rootType instanceof TypeDefinitionLink ) {
                    schemaRootElement.appendChild( createTypeDefinitionLink( ( TypeDefinitionLink ) rootType, true, typename ));
                }
                rootTypes.add( typename );
            }

        }

	private Message addRequestMessage( Definition localDef, OperationDeclaration op )
	{
		Message inputMessage = localDef.createMessage();
		inputMessage.setUndefined( false );

                Part inputPart = localDef.createPart();
		inputPart.setName( "body" );

                 // adding wsdl_types related to this message
                if ( op instanceof OneWayOperationDeclaration ) {
                    OneWayOperationDeclaration op_ow = ( OneWayOperationDeclaration ) op;

                     // set the message name as the name of the jolie request message type
                    inputMessage.setQName( new QName( tns, op_ow.requestType().id() ) );
                    addRootType( op_ow.requestType(), op_ow.id() );
                   
                } else {
                    RequestResponseOperationDeclaration op_rr = ( RequestResponseOperationDeclaration ) op;
                     // set the message name as the name of the jolie request message type
                    inputMessage.setQName( new QName( tns, op_rr.requestType().id() ) );
                    addRootType( op_rr.requestType(), op_rr.id() );
                   
                }
                // set the input part as the operation name
		inputPart.setElementName( new QName( tns_schema, op.id()  ) );
		
		inputMessage.addPart( inputPart );
		inputMessage.setUndefined( false );

		localDef.addMessage( inputMessage );
		return inputMessage;
	}

	private Message addResponseMessage( Definition localDef, OperationDeclaration op )
	{
		
		Message outputMessage = localDef.createMessage();
		outputMessage.setUndefined( false );

                Part outputPart = localDef.createPart();
		outputPart.setName( "body" );

                // adding wsdl_types related to this message
   
                RequestResponseOperationDeclaration op_rr = ( RequestResponseOperationDeclaration ) op;
                String outputPartName = op_rr.id() + "Response";
                  // set the message name as the name of the jolie response message type
                outputMessage.setQName(  new QName( tns, op_rr.responseType().id()) );
                addRootType( op_rr.responseType(), outputPartName );
                
		outputPart.setElementName( new QName( tns_schema,  outputPartName ) );

		outputMessage.addPart( outputPart );
		outputMessage.setUndefined( false );

		localDef.addMessage( outputMessage );

		
		return outputMessage;

	}

	private Message addFaultMessage( Definition localDef, OperationDeclaration op, TypeDefinition tp, String faultName )
	{
		Message faultMessage = localDef.createMessage();
                faultMessage.setUndefined( false );

                // set the fault message name as the name of the fault jolie message type
                faultMessage.setQName( new QName( tns, tp.id() ) );
		
		
                Part faultPart = localDef.createPart();
		faultPart.setName( "body" );

                String faultPartName = tp.id();
                 // adding wsdl_types related to this message
                addRootType( tp, faultPartName );
               
		faultPart.setElementName( new QName( tns_schema, faultPartName ) );
		faultMessage.addPart( faultPart );
                faultMessage.setUndefined( false );

               
		localDef.addMessage( faultMessage );
		return faultMessage;

	}

        private PortType createPortType( Definition def, String portTypeName ) {
            PortType pt = def.getPortType( new QName( portTypeName ) );
		if ( pt == null ) {
			pt = new PortTypeImpl();
		}

		pt.setUndefined( false );
		QName pt_QN = new QName( tns, portTypeName );

		pt.setQName( pt_QN );
		def.addPortType( pt );

                return pt;
        }


	private Operation addOWOperation2PT( Definition def, PortType pt, OneWayOperationDeclaration op )
	{
		Operation wsdlOp = def.createOperation();

		wsdlOp.setName( op.id() );
		wsdlOp.setStyle( OperationType.ONE_WAY );
		wsdlOp.setUndefined( false );

		Input in = def.createInput();
                Message msg_req = addRequestMessage( localDef, op  );
		msg_req.setUndefined( false );
		in.setMessage( msg_req );
		wsdlOp.setInput( in );
		wsdlOp.setUndefined( false );
		
                pt.addOperation( wsdlOp );
		
		return wsdlOp;
	}

        private Operation addRROperation2PT( Definition def, PortType pt, RequestResponseOperationDeclaration op )
	{
		Operation wsdlOp = def.createOperation();

		wsdlOp.setName( op.id() );
		wsdlOp.setStyle( OperationType.REQUEST_RESPONSE );
		wsdlOp.setUndefined( false );

                // creating input
		Input in = def.createInput();
                Message msg_req = addRequestMessage( localDef, op  );
		in.setMessage( msg_req );
		wsdlOp.setInput( in );

                // creating output
                Output out = def.createOutput();
		Message msg_resp = addResponseMessage( localDef, op  );
		out.setMessage( msg_resp );
		wsdlOp.setOutput( out );

                // creating faults
                for( Entry<String, TypeDefinition> curFault : op.faults().entrySet() ) {
                    Fault fault = localDef.createFault();
                    fault.setName( curFault.getKey()  );
                    Message flt_msg = addFaultMessage( localDef, op, curFault.getValue(), curFault.getKey() );
                    fault.setMessage( flt_msg );
                    wsdlOp.addFault( fault );

                }
                pt.addOperation( wsdlOp );

		return wsdlOp;
	}

        private Binding createBindingSOAP( Definition def, PortType pt, String bindingName ) {
                    Binding bind = def.getBinding( new QName( bindingName ) );
                    if ( bind == null ) {
                            bind = def.createBinding();
                            bind.setQName( new QName( tns, bindingName ) );
                    }
                    bind.setPortType( pt );
                    bind.setUndefined( false );
                    try {
                        SOAPBinding soapBinding = (SOAPBinding) extensionRegistry.createExtension( Binding.class, new QName( NameSpacesEnum.SOAP.getNameSpaceURI(), "binding" ) );
                        soapBinding.setTransportURI( NameSpacesEnum.SOAPoverHTTP.getNameSpaceURI() );
                        soapBinding.setStyle( "document" );
                        bind.addExtensibilityElement( soapBinding );
                    } catch( WSDLException ex ) {
			System.out.println( ( ex.getStackTrace() ));
                    }
                    def.addBinding( bind );

                    return bind;

        }

	private void addOperationSOAPBinding( Definition localDef, PortType portType, Operation wsdlOp, Binding bind )
	{
		try {
                        // creating operation binding
			BindingOperation bindOp = localDef.createBindingOperation();
			bindOp.setName( wsdlOp.getName() );

                        // adding soap extensibility elements
			SOAPOperation soapOperation = (SOAPOperation) extensionRegistry.createExtension( BindingOperation.class, new QName( NameSpacesEnum.SOAP.getNameSpaceURI(), "operation" ) );
			soapOperation.setStyle( "document" );
			//NOTA-BENE: Come settare SOAPACTION? jolie usa SOAP1.1 o 1.2? COme usa la SoapAction?
			soapOperation.setSoapActionURI( wsdlOp.getName() );
			bindOp.addExtensibilityElement( soapOperation );
			bindOp.setOperation( wsdlOp );

                        // adding input
                        BindingInput bindingInput = localDef.createBindingInput();
			SOAPBody body = (SOAPBody) extensionRegistry.createExtension( BindingInput.class, new QName( NameSpacesEnum.SOAP.getNameSpaceURI(), "body" ) );
			body.setUse( "literal" );
			bindingInput.addExtensibilityElement( body );
			bindOp.setBindingInput( bindingInput );

                        // adding output
			BindingOutput bindingOutput = localDef.createBindingOutput();
			bindingOutput.addExtensibilityElement( body );
			bindOp.setBindingOutput( bindingOutput );

                        // adding fault
                        if ( !wsdlOp.getFaults().isEmpty() ) {
                           Iterator it = wsdlOp.getFaults().entrySet().iterator();
                           while ( it.hasNext() ) {
                                BindingFault  bindingFault = localDef.createBindingFault();
                                SOAPFault soapFault = ( SOAPFault ) extensionRegistry.createExtension( BindingFault.class, new QName( NameSpacesEnum.SOAP.getNameSpaceURI(), "fault" ) );
                                soapFault.setUse("literal");
                                String faultName = ((Entry) it.next()).getKey().toString();
                                bindingFault.setName( faultName );
                                soapFault.setName( faultName );
                                bindingFault.addExtensibilityElement( soapFault );
                                bindOp.addBindingFault( bindingFault );
                            }
                        }
			
			bind.addBindingOperation( bindOp );
			

		} catch( WSDLException ex ) {
			ex.printStackTrace();
		}

	}

	public Service createService( Definition localdef, String serviceName, Binding bind, String mySOAPAddress )
	{
                Port p = localDef.createPort();
                p.setName( serviceName + "Port" );
		try {
			
			SOAPAddress soapAddress = (SOAPAddress) extensionRegistry.createExtension( Port.class, new QName( NameSpacesEnum.SOAP.getNameSpaceURI(), "address" ) );
			soapAddress.setLocationURI( mySOAPAddress );
			p.addExtensibilityElement( soapAddress );
                } catch( WSDLException ex ) {
			ex.printStackTrace();
		}
                p.setBinding( bind );
                Service s = new ServiceImpl();
                QName serviceQName = new QName( serviceName );
                s.setQName( serviceQName );
                s.addPort( p );
                localDef.addService( s );
                return s;
	}
	
	private Document createDOMdocument()
	{
		Document document;
		DocumentBuilder db = null;
		try {

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware( true );
			db = dbf.newDocumentBuilder();

		} catch( Exception e ) {
			e.printStackTrace();
		}
		document = db.newDocument();
		return document;
	}

	private Element createSchemaRootElement( Document document )
	{
		Element rootElement = document.createElement("xs:schema");
                rootElement.setAttribute( "xmlns:xs", NameSpacesEnum.XML_SCH.getNameSpaceURI() );
		rootElement.setAttribute( "targetNamespace", tns_schema );
		document.appendChild( rootElement );
		return rootElement;

        }

	public void setSchemaDocIntoWSDLTypes( Document doc )
	{
		try {
			
			Types wsdl_types = localDef.createTypes();
			Schema typesExt = (Schema) extensionRegistry.createExtension( Types.class, new QName( NameSpacesEnum.XML_SCH.getNameSpaceURI(), "schema" ) );
			typesExt.setElement( (Element) doc.getFirstChild() );
			wsdl_types.addExtensibilityElement( typesExt );
			localDef.setTypes( wsdl_types );

		} catch( Exception ex ) {
			System.err.println( ex.getMessage() );
		}
	}

	

}