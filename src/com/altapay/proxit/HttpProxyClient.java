package com.altapay.proxit;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.io.ByteStreams;


public class HttpProxyClient
{

	public static RawHttpMessage sendRequest(
		URL url,
		RawHttpMessage request) throws IOException
	{
		System.out.println("\n======= sendRequest(start: "+url+")");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setInstanceFollowRedirects(false);

		con.setRequestMethod(request.getRequestHttpMethod());
		System.out.println(request.getHttpRequestLine());
		for(RawHttpHeader h : request.getHeaders())
		{
			if(!h.matches("Content-Length")
					&& !h.matches("Accept-Encoding"))
			{
				System.out.println(h);
				con.setRequestProperty(h.getKey(),h.getValue());
			}
			else
			{
				System.out.println("(skipped) "+h);
			}
		}
		con.setRequestProperty("Accept-Encoding", "identity");
		con.setDoInput(true);
		
		if("POST".equals(request.getRequestHttpMethod()))
		{
			con.setRequestProperty("Content-length", ""+request.getContentLength()); 
			con.setDoOutput(true);
			con.getOutputStream().write(request.getBody());
			System.out.println("[wrote "+request.getBody().length+" bytes of body]");
		}
		
		System.out.println("======= sendRequest(end: "+url+")\n");
		
		RawHttpMessage response = readResponse(con);
		response.setId(request.getId());
		return response;
	}
	
	private static RawHttpMessage readResponse(HttpURLConnection con) throws IOException
	{
		System.out.println("\n======= readResponse(start: "+con.getURL()+")");
		
		RawHttpMessage response = new RawHttpMessage();
		response.setMessageType(RawHttpMessage.MessageType.RESPONSE);
		
		// Read the response headers
		response.setResponseCode(con.getResponseCode());
		response.setResponseMessage(con.getResponseMessage());
		for(Entry<String, List<String>> entry : con.getHeaderFields().entrySet())
		{
			for(String v : entry.getValue())
			{
				if(entry.getKey() != null)
				{
					System.out.println(entry.getKey()+": "+v);
					response.addHeader(entry.getKey(), v);
				}
				else
				{
					System.out.println(v);
				}
			}
		}
		System.out.println();
		
		response.setBody(ByteStreams.toByteArray(con.getInputStream()));
		System.out.println("[Body: ("+response.getBody().length+" bytes)]");
		System.out.println("======= readResponse(end: "+con.getURL()+")\n");
		
		return response;
	}

}
