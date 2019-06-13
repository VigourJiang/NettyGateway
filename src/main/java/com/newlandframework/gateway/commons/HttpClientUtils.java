/**
 * Copyright (C) 2018 Newland Group Holding Limited
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.newlandframework.gateway.commons;

import io.netty.handler.codec.http.HttpRequest;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.newlandframework.gateway.commons.GatewayOptions.GATEWAY_OPTION_CHARSET;

/**
 * @author tangjie<https://github.com/tang-jie>
 * @filename:HttpClientUtils.java
 * @description:HttpClientUtils功能模块
 * @blogs http://www.cnblogs.com/jietang/
 * @since 2018/4/18
 */
public class HttpClientUtils {
	public static StringBuilder post(String serverUrl, String xml, int timeout) {
		StringBuilder responseBuilder = null;
		BufferedReader reader = null;
		OutputStreamWriter wr = null;
		URL url;

		try {
			url = new URL(serverUrl);
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			conn.setConnectTimeout(timeout);
			wr = new OutputStreamWriter(conn.getOutputStream());

			wr.write(xml);
			wr.flush();

			reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), GATEWAY_OPTION_CHARSET.name()));
			responseBuilder = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				responseBuilder.append(line).append("\n");
			}
		} catch (IOException e) {
			System.out.println(e);
		} finally {
			if (wr != null) {
				try {
					wr.close();
				} catch (IOException e) {
				}
			}

			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}

			if (responseBuilder != null) {
				return responseBuilder;
			} else {
				throw GatewayOptions.GATEWAY_OPTION_SERVICE_ACCESS_ERROR;
			}
		}
	}

	public static String send(String serverUrl, byte[] xml, int timeout) {
		try {
			URL url = new URL(serverUrl);

			//1.创建socket用来与服务器端进行通信，发送请求建立连接，指定服务器地址和端口
			Socket socket = new Socket(url.getHost(), url.getPort());
			//2.获取输出流用来向服务器端发送登陆的信息
			OutputStream os = socket.getOutputStream();//字节输出流
			for(int i = 0; i < xml.length; i++){
				os.write(xml[i]);
			}
			os.flush();
			socket.shutdownOutput();//关闭输出流
			//3.获取输入流，用来读取服务器端的响应信息
			InputStream is = socket.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));//添加缓冲

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int i=-1;
			while((i=is.read())!=-1){
				baos.write(i);
            }

			//4.关闭其他相关资源
			br.close();
			is.close();
			os.close();
			socket.close();

			return baos.toString();
		} catch (IOException e) {
			System.out.println(e);
			throw GatewayOptions.GATEWAY_OPTION_SERVICE_ACCESS_ERROR;
		}
	}


	public static ParsedHttpResp get(String url, HttpRequest srcReq) throws Exception {
//创建默认的httpClient实例.
		CloseableHttpClient httpclient = null;
		//接收响应结果
		CloseableHttpResponse response = null;
		try {
			//创建httppost
			httpclient = HttpClients.createDefault();
			HttpGet httpGet = new HttpGet(url);
			for(Map.Entry<String, String> header : srcReq.headers()) {
				if(!header.getKey().toLowerCase().equals("content-length")) {
					httpGet.setHeader(header.getKey(), header.getValue());
				}
			}

			httpGet.setHeader("Cache-Control", "no-cache");
			response = httpclient.execute(httpGet);
			ParsedHttpResp resp = new ParsedHttpResp();
			resp.headers = response.getAllHeaders();
			HttpEntity entity = response.getEntity();
			if(entity != null){
				resp.body = EntityUtils.toByteArray(entity);
			}
			return resp;
		} catch (Exception e) {
			throw e;
		}finally{
			httpclient.close();
//			response.close();
		}

	}

		public static class ParsedHttpResp {
		public Header[] headers;
		public byte[] body;

	}


	public static ParsedHttpResp postMultiPart(String url, HttpRequest srcReq, byte[] content)throws Exception{
		//创建默认的httpClient实例.
		CloseableHttpClient httpclient = null;
		//接收响应结果
		CloseableHttpResponse response = null;
		try {
			//创建httppost
			httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(url);
			final StringBuilder sb = new StringBuilder();
			String splitter = "";
			for(Map.Entry<String, String> header : srcReq.headers()) {
				if(!header.getKey().toLowerCase().equals("content-length") &&
						!header.getKey().toLowerCase().equals("content-type")
						) {
					httpPost.setHeader(header.getKey(), header.getValue());
				}
				if(header.getKey().toLowerCase().equals("content-type")) {
					splitter = header.getValue().split("boundary=")[1];
				}
			}

			//创建 MultipartEntityBuilder,以此来构建我们的参数
			MultipartEntityBuilder EntityBuilder = MultipartEntityBuilder.create();
			ContentType contentType=ContentType.create("text/plain", Charset.forName("UTF-8"));
			// https://tools.ietf.org/html/rfc2046
			// 5.1.  Multipart Media Type
			String[] multiparts = new String(content, "ascii").split("--" + splitter + "\r\n");
			int totoalLen = 0;
			for(String multipart : multiparts) {
				String[] lines = multipart.split("\r\n");
				int lineNo = 0;
				int charCounts = 0;
				while(lineNo < lines.length) {
					String line = lines[lineNo];
					if(line.startsWith("Content-Disposition")) {
						String[] parts = line.split(";");
						for(String part : parts){
							if(part.trim().startsWith("name=")) {
								String key = part.trim().split("=")[1];
								key = key.substring(1, key.length() - 1);

								charCounts += (2+lines[lineNo].length());
								lineNo++;

								while(!lines[lineNo].equals("")){
									charCounts += (2+lines[lineNo].length());
									lineNo++;
								}

								charCounts += (2+lines[lineNo].length());
								lineNo++;

								String value = lines[lineNo];
								if(line.contains("filename=")) {
									int imgLength = multipart.length() - charCounts;
									byte[] imgData = new byte[imgLength];
									System.arraycopy(content, totoalLen + charCounts,
											imgData, 0, imgLength);

									System.out.println(imgData.length);
									Files.write(Paths.get("./output.jpg"), imgData,
											StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
									EntityBuilder.addPart(key,
											new ByteArrayBody(imgData, "aa.jpg"));
								} else {
									EntityBuilder.addPart(key, new StringBody(value, contentType));
								}
							}
						}

					}
					charCounts += (2 + lines[lineNo].length());
					lineNo++;
				}

				totoalLen += multipart.length();
				totoalLen += (2 + splitter.length() + 2);
			}

			httpPost.setEntity(EntityBuilder.build());
			response = httpclient.execute(httpPost);
			ParsedHttpResp resp = new ParsedHttpResp();
			resp.headers = response.getAllHeaders();
			HttpEntity entity = response.getEntity();
			if(entity != null){
				resp.body = EntityUtils.toByteArray(entity);
			}
			return resp;
		} catch (Exception e) {
			throw e;
		}finally{
			httpclient.close();
//			response.close();
		}
	}

	public static ParsedHttpResp postJson(String url, HttpRequest srcReq, byte[] content) throws Exception{
		//创建默认的httpClient实例.
		CloseableHttpClient httpclient = null;
		//接收响应结果
		CloseableHttpResponse response = null;
		try {
			//创建httppost
			httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(url);
			final StringBuilder sb = new StringBuilder();
			for(Map.Entry<String, String> header : srcReq.headers()) {
				if(!header.getKey().toLowerCase().equals("content-length")) {
					httpPost.setHeader(header.getKey(), header.getValue());
				}
				if(header.getKey().equals("Content-Type")) {
					if(header.getValue().startsWith("multipart/form-data")){
						return postMultiPart(url, srcReq, content);
					}
				}
			}

			//参数
			StringEntity se = new StringEntity(new String(content, "utf-8"));
//			se.setContentEncoding("UTF-8");
//			se.setContentType("application/json");//发送json需要设置contentType
			httpPost.setEntity(se);
			response = httpclient.execute(httpPost);
			ParsedHttpResp resp = new ParsedHttpResp();
			resp.headers = response.getAllHeaders();
			HttpEntity entity = response.getEntity();
			if(entity != null){
				resp.body = EntityUtils.toByteArray(entity);
			}
			return resp;
		} catch (Exception e) {
			throw e;
		}finally{
			httpclient.close();
//			response.close();
		}
	}
}

