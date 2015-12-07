package com.altapay.proxit;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTMLUrlRewriter
{
	public StringBuffer makeUrlsAbsolute(URL url, StringBuffer html)
	{
		for(String quoteChar : new String[]{"\"", "'"})
		{
			html = makeAbsolute(url, html, "(<img[^>]*)(src="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<script[^>]*)(src="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<link[^>]*rel="+quoteChar+"[^"+quoteChar+"]+"+quoteChar+"[^>]*)(href="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<link[^>]*)(href="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*rel="+quoteChar+"[^"+quoteChar+"]+"+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<link[^>]*rel="+quoteChar+"icon"+quoteChar+"[^>]*)(href="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<link[^>]*)(href="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*rel="+quoteChar+"icon"+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<a[^>]*)(href="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<form[^>]*)(action="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<meta[^>]*)(http-equiv="+quoteChar+"refresh"+quoteChar+"\\s*content="+quoteChar+"\\d+;url=)([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			html = makeAbsolute(url, html, "(<input[^>]*)(src="+quoteChar+")([^"+quoteChar+"]*)("+quoteChar+"[^>]*>)");
			
			// TODO: html = preg_replace_callback('/(<[a-z]*[^>]*style='.$qouteChar.')([^'.$qouteChar.']*)('.$qouteChar.'[^>]*>)/i',                                  array($this, 'fixInlineStyleSheetURLsByLoadingThroughOurGateway'), $html);
		}
		
		return html;
	}

	private StringBuffer makeAbsolute(URL url, StringBuffer html, String pattern)
	{
		Pattern regex = Pattern.compile(pattern);
		Matcher regexMatcher = regex.matcher(html);
		StringBuffer resultString = new StringBuffer();
		while (regexMatcher.find())
		{
			String abs = regexMatcher.group(1)+regexMatcher.group(2)+createAbsoluteUrl(url, regexMatcher.group(3))+regexMatcher.group(4);
			regexMatcher.appendReplacement(resultString, abs);
		}
		html = regexMatcher.appendTail(resultString);
		return html;
	}

	public String createAbsoluteUrl(URL url, String relative)
	{
		try
		{
			if(relative.startsWith("data:image"))
			{
				return relative;
			}
			return new URL(url, relative).toString();
		}
		catch (MalformedURLException e)
		{
			return "badurl";
		}
	}
	
}
