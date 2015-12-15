package com.altapay.proxit;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.io.ByteStreams;


public class HttpProxyClient
{
	public static void setDoNotVerifySslCertificates() {
		
		try
		{
			// Create a trust manager that does not validate certificate chains
			X509TrustManager trustManager = new X509TrustManager()
			{
				public java.security.cert.X509Certificate[] getAcceptedIssuers()
				{
					return null;
				}
				
				public void checkClientTrusted(
					X509Certificate[] certs,
					String authType)
				{
					// fine
				}
				
				public void checkServerTrusted(
					X509Certificate[] certs,
					String authType)
				{
				}
			};
			
			// Install the all-trusting trust manager
			final SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null,
				new TrustManager[]{ trustManager },
				new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			
			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier()
			{
				@Override
				public boolean verify(String hostname, SSLSession session)
				{
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		}
		catch(Exception e)
		{
			e.printStackTrace(System.out);
		}
	}

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
