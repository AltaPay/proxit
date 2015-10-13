package com.altapay.proxit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name="http")
public class RawHttpMessage
{
	private UUID id;
	private UUID connectionId;
	ArrayList<String> headers = new ArrayList<>();
	private String body;
	public enum MessageType {
		REQUEST,
		RESPONSE
	}
	private MessageType type = MessageType.REQUEST;
	private Socket socket;
	
	public RawHttpMessage()
	{
	}
	
	public void setId(UUID id)
	{
		this.id = id;
	}

	public UUID getId()
	{
		return id;
	}

	public MessageType getMessageType()
	{
		return type;
	}

	public void setMessageType(MessageType type)
	{
		this.type = type;
	}

	public void addHeader(String header)
	{
		headers.add(header);
	}
	
	public void setHeaders(ArrayList<String> headers)
	{
		this.headers = headers;
	}
	
	public ArrayList<String> getHeaders()
	{
		return headers;
	}
	
	@XmlTransient
	public void setBody(String body)
	{
		this.body = body;
	}
	
	public String getBody()
	{
		return body;
	}
	
	@XmlTransient
	public Socket getSocket()
	{
		return socket;
	}

	public void setSocket(Socket socket)
	{
		this.socket = socket;
	}
	
	@XmlTransient
	public UUID getConnectionId()
	{
		return connectionId;
	}

	public void setConnectionId(UUID connectionId)
	{
		this.connectionId = connectionId;
	}
	
	
	public String getBase64Body()
	{
		return (body != null ? DatatypeConverter.printBase64Binary(body.getBytes()) : null);
	}

	public void setBase64Body(String body)
	{
		this.body = (body != null ? new String(DatatypeConverter.parseBase64Binary(body)) : null);
	}

	public void writeXmlBytes(OutputStream out)
	{
		try
		{
			JAXBContext context = JAXBContext.newInstance(RawHttpMessage.class);
			context.createMarshaller().marshal(this, out);
			out.write("\n".getBytes());
		}
		catch (JAXBException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@Override
	public String toString()
	{
		return headers+(body != null ? body : "");
	}
	
	public static RawHttpMessage get404Response(UUID id, String error)
	{
		String body = "<html>\n"
				+ "<head>\n"
				+ "	<title>Error</title>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "	<p>We experienced an error trying to proxy your request to the developer in question (error message: "+error+")</p>\n"
				+ "</body>\n"
				+ "</html>";
		RawHttpMessage response = new RawHttpMessage();
		response.setId(id);
		response.setMessageType(MessageType.RESPONSE);
		response.addHeader("HTTP/1.1 404 File Not Found");
		response.addHeader("Server: AltaPay DevProxy/2015.10.13 (Ubuntu)");
		response.addHeader("Content-Length: "+body.length());
		response.addHeader("Connection: close");
		response.addHeader("Content-Type: text/html");
		response.addHeader("");
		
		response.setBody(body);
		
		return response;
	}
}
