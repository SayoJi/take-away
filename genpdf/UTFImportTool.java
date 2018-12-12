package com.pru.hk.util.velocity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.log4j.Logger;

public class UTFImportTool {

	private Logger logger = Logger.getLogger(this.getClass());

	public static String read(String url) throws IOException, Exception {
		// absolute URL
		URLConnection uc = null;
		HttpURLConnection huc = null;
		InputStream i = null;
		Reader r = null;
		String charSet = "UTF-8";
		BufferedReader in = null;
		StringBuffer sb = null;

		try {
			// handle absolute URLs ourselves, using java.net.URL
			URL u = new URL(url);
			// URL u = new URL("http", "proxy.hi.is", 8080, target);
			uc = u.openConnection();
			i = uc.getInputStream();

			// check response code for HTTP URLs, per spec,
			if (uc instanceof HttpURLConnection) {
				huc = (HttpURLConnection) uc;

				int status = huc.getResponseCode();
				if (status < 200 || status > 299) {
					throw new Exception(status + " " + url);
				}
			}

			// okay, we've got a stream; encode it appropriately
			r = new InputStreamReader(i, charSet);
			in = new BufferedReader(r);
			String inputLine;
			sb = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				sb.append(inputLine);
				sb.append("\n");
			}
			return sb.toString();
		} catch (UnsupportedEncodingException ex) {
			throw new Exception("Unsupported encoding" + " " + "UTF-8", ex);
		} catch (Exception ex) {
			ex.printStackTrace();
			return "Error reading benifit table";
		}

		finally {
			if (in != null) {
				in.close();
			}

			if (huc != null) {
				huc.disconnect();
			}
		}
	}
}
