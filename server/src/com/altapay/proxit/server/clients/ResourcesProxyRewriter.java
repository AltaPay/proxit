package com.altapay.proxit.server.clients;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourcesProxyRewriter
{

	public StringBuffer proxyResourceUrls(String proxyCallbackUrl, StringBuffer html)
	{
		//System.out.println("Before: \n"+html);
		for(String quoteChar : new String[]{"\"", "'"})
		{
			html = unproxyResourceUrl(proxyCallbackUrl, html, "(<[a-zA-Z][a-zA-Z0-9]*[^>]*)(src="+quoteChar+")(https?://[^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = unproxyResourceUrl(proxyCallbackUrl, html, "(<link[^>]*)(href="+quoteChar+")(https?://[^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");

			// TODO: html = preg_replace_callback('/(<[a-z]*[^>]*style='.$qouteChar.')([^'.$qouteChar.']*)('.$qouteChar.'[^>]*>)/i',                                  array($this, 'fixInlineStyleSheetURLsByLoadingThroughOurGateway'), $html);
		}
		
		//return new StringBuffer("<html>HEst</html>");
		
		//System.out.println("After: \n"+html);
		return html;
	}

	private StringBuffer unproxyResourceUrl(String proxyCallbackUrl, StringBuffer html, String pattern)
	{
		Pattern regex = Pattern.compile(pattern);
		Matcher regexMatcher = regex.matcher(html);
		StringBuffer resultString = new StringBuffer();
		while (regexMatcher.find())
		{
			String abs = regexMatcher.group(1)+regexMatcher.group(2)+proxyCallbackUrl+regexMatcher.group(3)+regexMatcher.group(4);
			regexMatcher.appendReplacement(resultString, abs);
		}
		html = regexMatcher.appendTail(resultString);
		return html;
	}

}
