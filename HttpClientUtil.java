package com.jf.ams.notify.common.util;

import com.jf.common.tools.prop.PropertyManager;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * http连接工具类
 * 
 * @author   brilliance.ke
 * @version  1.0   2016-08-31
 * @version  2.0   2016-12-01   
 */
public class HttpClientUtil {

	/** 日志 */
	private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

	/** 连接超时编码  */
	public final int STS_TIMEOUT_CONN = -1;
	/** 响应超时编码 */
	public final int STS_TIMEOUT_SOCK = -2;
	/** 其他编码 */
	public final int STS_OTHER = -9;
	/** 非阻塞 I/O超时时间 */
	private final static int SO_TIME_OUT = 1000;
	/** 请求连接(连接池中)超时时间  */
	private static int CON_REQ_TIME_OUT = 2000;
	/** 建立链接超时时间  */
	private static int CON_TIME_OUT = 2000;
	/** SOCKET 超时时间 */
	private static int SOCKET_TIME_OUT = 3000;
	/**  整个连接池最大连接数 */
	private static int MAX_TOTAL = 500;
	/**  每路由最大连接数，默认值是2  */
	private static int MAX_ROUTE = 400;

	/** http连接工具类单利对象 */
	private static final HttpClientUtil CLIENT_UTIL;
	/**  */
	private static PoolingHttpClientConnectionManager cm ;

    private static ConnectionKeepAliveStrategy myStrategy;

	private static RequestConfig config;

	private CloseableHttpClient client;

	static {
		init();
		//
		CLIENT_UTIL = new HttpClientUtil();
	}

	/**
	 * 配置连接池参数
	 *
	 * @return
	 */
	private static void init() {

		try{

			SOCKET_TIME_OUT=Integer.parseInt(PropertyManager.getString("http_client_socket_timeout"));
			CON_TIME_OUT=Integer.parseInt(PropertyManager.getString("http_client_connectiontimeout"));
			CON_REQ_TIME_OUT=Integer.parseInt(PropertyManager.getString("http_client_connectionrequest_timeout"));
			MAX_ROUTE=Integer.parseInt(PropertyManager.getString("http_client_max_perroute"));
			MAX_TOTAL=Integer.parseInt(PropertyManager.getString("http_client_max_poolcount"));

		}catch(Exception e){
			logger.error("http 连接池初始化参数失败"+e.getMessage(),e);
		}

		final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("http", PlainConnectionSocketFactory.INSTANCE)
				.register("https", SSLConnectionSocketFactory.getSystemSocketFactory()).build();
		final ManagedHttpClientConnectionFactory connectionFactory = new ManagedHttpClientConnectionFactory(DefaultHttpRequestWriterFactory.INSTANCE,
				DefaultHttpResponseParserFactory.INSTANCE);
		cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry, connectionFactory);
		final SocketConfig defaultSocketConfig = SocketConfig.custom().setTcpNoDelay(true).build();
		cm.setDefaultSocketConfig(defaultSocketConfig);
		cm.setMaxTotal(MAX_TOTAL);
		cm.setDefaultMaxPerRoute(MAX_ROUTE);
		cm.setValidateAfterInactivity(5*1000);
		config = RequestConfig.custom().setConnectTimeout(CON_TIME_OUT)
				.setSocketTimeout(SOCKET_TIME_OUT)
				.setConnectionRequestTimeout(CON_REQ_TIME_OUT)
				.build();
	}

	/**
	 * 获取链接
	 *
	 * @return
	 */
	private CloseableHttpClient getConnection() {

		if(null == client){
			synchronized (this){
				if(null == client){
					client = HttpClients.custom().setConnectionManager(cm)
							.setConnectionManagerShared(false)
							.evictExpiredConnections()
							.setConnectionTimeToLive(60, TimeUnit.SECONDS)
							.setDefaultRequestConfig(config)
							.setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
							.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
							.setRetryHandler(new DefaultHttpRequestRetryHandler(0,false))
							.build();

					Runtime.getRuntime().addShutdownHook(new Thread(){

						@Override
						public void run() {
							try {
								client.close();
							} catch (IOException e) {
								logger.error(e.getMessage(),e);
							}
						}
					});
				}
			}
		}
		return client;
	}

	/**
	 *
	 *
	 * @param paraMap
	 * @param charSet
	 * @return
	 * @throws Exception
	 */
	private UrlEncodedFormEntity mapToEntity(Map<Object, Object> paraMap, Charset charSet) throws Exception {

		//实体
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		//赋值
		for (Object key : paraMap.keySet()) {
			params.add(new BasicNameValuePair(key.toString(), (String)paraMap.get(key)));
		}

		return new UrlEncodedFormEntity(params,charSet);
	}

	/**
	 * url 拼接参数
	 *
	 * @param strUrl
	 * @param paras
	 * @return
	 */
	private String urlAppend(String strUrl,String paras) {

		Args.notNull(strUrl, "url request");

		if (strUrl.indexOf("?") > 0) {
			strUrl = strUrl +"&" + paras;
		} else {
			strUrl = strUrl +"?" + paras;
		}

		return strUrl;
	}


	/**
	 * http请求响应信息
	 *
	 * @author brilliance.ke
	 *
	 */
	public class HttpResRtn {
		/** 响应编码 */
		private int stsCode;
		/** 响应原因短语 */
		private String rsnPhrase;
		/** 响应值 */
		private String value;

		/**
		 * 构造方法
		 */
		private HttpResRtn() {

		}

		/**
		 * 获取响应编码
		 *
		 * @return
		 */
		public int getStsCode() {
			return stsCode;
		}

		/**
		 * 设置响应编码
		 *
		 * @param stsCode
		 */
		protected void setStsCode(int stsCode) {
			this.stsCode = stsCode;
		}

		/**
		 * 获取原因原语
		 *
		 * @return
		 */
		public String getRsnPhrase() {
			return rsnPhrase;
		}

		/**
		 * 设置原因原语
		 *
		 * @param rsnPhrase
		 */
		protected void setRsnPhrase(String rsnPhrase) {
			this.rsnPhrase = rsnPhrase;
		}

		/**
		 * 获取返回信息
		 *
		 * @return
		 */
		public String getValue() {
			return value;
		}

		/**
		 * 设置返回信息
		 *
		 * @param value
		 */
		protected void setValue(String value) {
			this.value = value;
		}

	}


	/**
	 * 私有化构造方法
	 */
	private HttpClientUtil() {
		
	}
	
	/**
	 * 获取当前对象，单例对象
	 * @return
	 */
	public static HttpClientUtil getInstance() {
		
		return CLIENT_UTIL;
	}
	
	
	/**
	 * post请求 参数为json串
	 *
	 * @param strUrl    请求URL
	 * @param strJson   请求json字符串
	 * @param reqConfig 请求连接配置参数
	 * @return
	 */
	private HttpResRtn postJson(String strUrl, String strJson,RequestConfig reqConfig) {
		// 参数校验
		Args.notNull(strUrl, "url request");
		Args.notNull(strJson, "json string request");
		
		//响应信息对象
		HttpResRtn resRtn = new HttpResRtn();
		//post
		HttpPost postMethod = null;
		//
		HttpResponse response = null;
		
		try {
			//实体
			StringEntity entity = new StringEntity(strJson.toString(),"utf-8");
			//post
			postMethod = new HttpPost(strUrl);
			postMethod.setEntity(entity);
			//设置请求和传输超时时间
			if (reqConfig!= null) {
				postMethod.setConfig(reqConfig);
			}
			//请求服务
			long startTime = System.currentTimeMillis();
			response = getConnection().execute(postMethod);
			long endTime = System.currentTimeMillis();

			//响应
			StatusLine stsLine = response.getStatusLine();
			//状态码
			int stsCode = stsLine.getStatusCode();
			logger.info("调用API 花费时间(单位：毫秒)：" + (endTime - startTime)+"，statusCode：" + stsCode+"，请求参数===>"+strJson);
			
			//封装返回信息
			resRtn.setStsCode(stsCode);
			resRtn.setRsnPhrase(stsLine.getReasonPhrase());
			
			//Read the response body
			if (stsCode == HttpStatus.SC_OK) {
				String strValue = EntityUtils.toString(response.getEntity(),"utf-8"); 
				resRtn.setValue(strValue);
				logger.info("http response body is:" + strValue);
			}  
			
			response.getEntity().getContent().close();
		} catch (ConnectTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (SocketTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_SOCK);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (HttpHostConnectException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (Exception e) {

			resRtn.setStsCode(STS_OTHER);
			resRtn.setRsnPhrase(e.getMessage());
		} finally {
			//释放资源
			if (response!= null) {
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}

		}
		
		return resRtn;
		
	}
	

	/**
	 * POST提交
	 * 
	 * @param strUrl 请求URL
	 * @param strJson 请求json字符串
	 * @return
	 */
	public HttpResRtn postJson(String strUrl, String strJson) {
		
		return postJson(strUrl, strJson, null);
	}
	
	/**
	 * post请求 参数为json串
	 *
	 * @param strUrl        请求URL
	 * @param strJson       请求json字符串
	 * @param socetTimeOut  响应超时时间
	 * @param conTimeOut    请求超时时间
	 * @return
	 */
	public HttpResRtn postJson(String strUrl, String strJson,int socetTimeOut,int conTimeOut) {
	
		//设置请求和传输超时时间
		RequestConfig reqConfig = null;

		/*reqConfig = RequestConfig.custom()
				.setSocketTimeout(socetTimeOut)
				.setConnectTimeout(conTimeOut)
				.build();*/
		
		return postJson(strUrl,strJson,reqConfig);
	}
	
	/**
	 * post请求 参数为json串
	 *
	 * @param strUrl       请求URL
	 * @param strJson      请求参数Json串
	 * @param socetTimeOut 响应超时时间
	 * @return
	 */
	public HttpResRtn postJson(String strUrl, String strJson,int socetTimeOut) {

		//设置请求和传输超时时间
		RequestConfig reqConfig = null;

		/*reqConfig = RequestConfig.custom()
				.setSocketTimeout(socetTimeOut)
				.setConnectTimeout(conTimeOut)
				.build();*/
		
		return postJson(strUrl,strJson,reqConfig);
	}
	

	/**
	 * post请求，参数为Map
	 *
	 * @param strUrl    请求URL
	 * @param paraMap   请求Map对象
	 * @param reqConfig 请求连接配置参数
	 * @return
	 * @throws Exception
	 */
	private HttpResRtn postMap(String strUrl, Map<Object, Object> paraMap, RequestConfig reqConfig) {
		
		// 参数校验
		Args.notNull(strUrl, "url request");
		Args.notNull(paraMap, "json string request");
		
		//响应信息对象
		HttpResRtn resRtn = new HttpResRtn();
		//post
		HttpPost postMethod = null;
		//
		HttpResponse response = null;
		
		try {
			/**  设置请求参数 */
			//实体
			UrlEncodedFormEntity urlEntity = mapToEntity(paraMap,Consts.UTF_8);
			
			//post
			postMethod = new HttpPost(strUrl);
			postMethod.setEntity(urlEntity);  
			//设置请求和传输超时时间
			if (reqConfig!= null) {
				postMethod.setConfig(reqConfig);
			}
			//请求时间`
			long startTime = System.currentTimeMillis();
			//响应
			response = getConnection().execute(postMethod);
			long endTime = System.currentTimeMillis();
			logger.info("调用API 花费时间(单位：毫秒)：" + (endTime - startTime));
			
			//响应
			StatusLine stsLine = response.getStatusLine();
			//状态码
			int stsCode = stsLine.getStatusCode();
			logger.info("statusCode:" + stsCode);
			
			//封装返回信息
			resRtn.setStsCode(stsCode);
			resRtn.setRsnPhrase(stsLine.getReasonPhrase());
			
			int statusCode = response.getStatusLine().getStatusCode();
			logger.info("statusCode:" + statusCode);
			
			//Read the response body
			if (stsCode == HttpStatus.SC_OK) {
				String strValue = EntityUtils.toString(response.getEntity());
				resRtn.setValue(strValue);
				logger.info("http response body is:" + strValue);
			}
		} catch (ConnectTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (SocketTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_SOCK);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (HttpHostConnectException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (Exception e) {
			
			resRtn.setStsCode(STS_OTHER);
			resRtn.setRsnPhrase(e.getMessage());
		} finally {
			//释放资源
			if (response!= null) {
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
		
		return resRtn;
	}
	
	
	/**
	 * POST提交
	 * 
	 * @param strUrl 请求URL
	 * @param paraMap 请求map
	 * @return
	 */
	public HttpResRtn postMap(String strUrl,Map<Object, Object> paraMap) throws Exception {
		
		
		return postMap(strUrl,paraMap,null);
	}
	
	
	/**
	 * POST提交 参数为Map对象
	 * 
	 * @param strUrl        请求URL
	 * @param paraMap       请求Map字符串
	 * @param socketTimeOut 响应超时时间(单位毫秒)
	 * @return
	 */
	public HttpResRtn postMap(String strUrl,Map<Object, Object> paraMap,int socketTimeOut) {
		
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socketTimeOut)
											   .setConnectTimeout(CON_TIME_OUT)
											   .build();

		return postMap(strUrl,paraMap,reqConfig);
	}
	
	/**
	 * post请求 参数为json串
	 *
	 * @param strUrl        请求URL
	 * @param paraMap       请求Map
	 * @param socetTimeOut  响应超时时间
	 * @param conTimeOut    请求超时时间
	 * @return
	 */
	public HttpResRtn postMap(String strUrl, Map<Object, Object> paraMap,int socetTimeOut,int conTimeOut) {
	
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socetTimeOut)
											   .setConnectTimeout(conTimeOut)
											   .build();
		
		return postMap(strUrl,paraMap,reqConfig);
	}
	
	
	/**
	 * get请求 参数为json串
	 *
	 * @param strUrl    请求URL
	 * @param strJson   请求json字符串
	 * @param reqConfig 请求连接配置参数
	 * @return
	 */
	private HttpResRtn getJson(String strUrl, String strJson,RequestConfig reqConfig) {
		// 参数校验
		Args.notNull(strUrl, "url request");
		Args.notNull(strJson, "json string request");
		
		//响应信息对象
		HttpResRtn resRtn = new HttpResRtn();
		//get
		HttpGet getMethod = null;
		//
		HttpResponse response = null;
		
		try {
			//实体
			StringEntity entity = new StringEntity(strJson.toString(),"utf-8");
			//get
			String strPara = EntityUtils.toString(entity);
			getMethod = new HttpGet(urlAppend(strUrl,strPara));
			if (reqConfig!= null) {
				getMethod.setConfig(reqConfig);
			}
			
			//请求服务
			long startTime = System.currentTimeMillis();
			response = getConnection().execute(getMethod);
			long endTime = System.currentTimeMillis();
			//
			logger.info("调用API 花费时间(单位：毫秒)：" + (endTime - startTime));
			//响应
			StatusLine stsLine = response.getStatusLine();
			//状态码
			int stsCode = stsLine.getStatusCode();
			logger.info("statusCode:" + stsCode);
			
			//封装返回信息
			resRtn.setStsCode(stsCode);
			resRtn.setRsnPhrase(stsLine.getReasonPhrase());
			
			//Read the response body
			if (stsCode == HttpStatus.SC_OK) {
				String strValue = EntityUtils.toString(response.getEntity(),"utf-8"); 
				resRtn.setValue(strValue);
				logger.info("http response body is:" + strValue);
			}  
			
			response.getEntity().getContent().close();
		} catch (ConnectTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (SocketTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_SOCK);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (HttpHostConnectException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (Exception e) {
			
			resRtn.setStsCode(STS_OTHER);
			resRtn.setRsnPhrase(e.getMessage());
		} finally {
			//释放资源
			if (response!= null) {
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
		
		return resRtn;
		
	}
	

	/**
	 * get提交
	 * 
	 * @param strUrl 请求URL
	 * @param strJson 请求json字符串
	 * @return
	 */
	public HttpResRtn getJson(String strUrl, String strJson) {
		
		return getJson(strUrl, strJson, null);
	}
	
	/**
	 * get请求 参数为json串
	 *
	 * @param strUrl        请求URL
	 * @param strJson       请求json字符串
	 * @param socetTimeOut  响应超时时间
	 * @param conTimeOut    请求超时时间
	 * @return
	 */
	public HttpResRtn getJson(String strUrl, String strJson,int socetTimeOut,int conTimeOut) {
	
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socetTimeOut)
											   .setConnectTimeout(conTimeOut)
											   .build();
		
		return getJson(strUrl,strJson,reqConfig);
	}
	
	/**
	 * get请求 参数为json串
	 *
	 * @param strUrl       请求URL
	 * @param strJson      请求参数Json串
	 * @param socetTimeOut 响应超时时间
	 * @return
	 */
	public HttpResRtn getJson(String strUrl, String strJson,int socetTimeOut) {
		
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socetTimeOut)
											   .setConnectTimeout(CON_TIME_OUT)
											   .build();
		
		return getJson(strUrl,strJson,reqConfig);
	}
	
	
	/**
	 * GET提交MAP参数
	 * 
	 * @param strUrl 请求URL
	 * @param paraMap 请求MAP对象
	 * @param reqConfig 请求连接配置参数
	 * @return
	 */
	private HttpResRtn getMap(String strUrl,Map<Object, Object> paraMap, RequestConfig reqConfig) {
		
		// 参数校验
		Args.notNull(strUrl, "url request");
//		Args.notNull(paraMap, "json string request");
		
		//响应信息对象
		HttpResRtn resRtn = new HttpResRtn();
		//get
		HttpGet getMethod = null;
		//
		HttpResponse response = null;
		
		try {
			/**  设置请求参数 */
			//实体
			UrlEncodedFormEntity urlEntity = mapToEntity(paraMap,Consts.UTF_8);
			
			String strPara = EntityUtils.toString(urlEntity);
			//get
			getMethod = new HttpGet(urlAppend(strUrl,strPara));
			if (reqConfig!= null) {
				getMethod.setConfig(reqConfig);
			}
			//响应
			response = getConnection().execute(getMethod);
			//响应
			StatusLine stsLine = response.getStatusLine();
			//状态码
			int stsCode = stsLine.getStatusCode();
			logger.info("statusCode:" + stsCode);
			
			//封装返回信息
			resRtn.setStsCode(stsCode);
			resRtn.setRsnPhrase(stsLine.getReasonPhrase());
			
			int statusCode = response.getStatusLine().getStatusCode();
			logger.info("statusCode:" + statusCode);
			
			//Read the response body
			if (stsCode == HttpStatus.SC_OK) {
				String strValue = EntityUtils.toString(response.getEntity());
				resRtn.setValue(strValue);
				logger.info("http response body is:" + strValue);
			}
		} catch (ConnectTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (SocketTimeoutException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_SOCK);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (HttpHostConnectException e) {
			
			resRtn.setStsCode(STS_TIMEOUT_CONN);
			resRtn.setRsnPhrase(e.getMessage());
		} catch (Exception e) {
			
			resRtn.setStsCode(STS_OTHER);
			resRtn.setRsnPhrase(e.getMessage());
		} finally {
			//释放资源
			if (response!= null) {
				try {
					EntityUtils.consume(response.getEntity());
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
		
		return resRtn;
	}
	
	
	/**
	 * get提交
	 * 
	 * @param strUrl 请求URL
	 * @param paraMap 请求map
	 * @return
	 */
	public HttpResRtn getMap(String strUrl,Map<Object, Object> paraMap) throws Exception {
		
		return getMap(strUrl,paraMap,null);
	}
	
	/**
	 * GET提交 参数为Map对象
	 * 
	 * @param strUrl        请求URL
	 * @param paraMap       请求Map字符串
	 * @param socketTimeOut 响应超时时间(单位毫秒)
	 * @return
	 * @throws Exception 
	 */
	public HttpResRtn getMap(String strUrl,Map<Object, Object> paraMap,int socketTimeOut) {
		
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socketTimeOut)
											   .setConnectTimeout(CON_TIME_OUT)
											   .build();

		return getMap(strUrl,paraMap,reqConfig);
	}
	
	/**
	 * GET提交
	 * 
	 * @param strUrl         请求URL
	 * @param paraMap        请求Map字符串
	 * @param socketTimeOut  响应超时时间(单位毫秒)
	 * @param conTimeOut     请求超时时间(单位毫秒)
	 * @return
	 */
	public HttpResRtn getMap(String strUrl,Map<Object, Object> paraMap,int socketTimeOut,int conTimeOut) {
		
		//设置请求和传输超时时间
		RequestConfig reqConfig = RequestConfig.custom()
											   .setSocketTimeout(socketTimeOut)
											   .setConnectTimeout(conTimeOut)
											   .build();

		return getMap(strUrl,paraMap,reqConfig);
	}

	public static void main(String[] args) {
		final HttpResRtn httpResRtn = HttpClientUtil.getInstance().postJson("https://test-cgams.9fbank.com/ams/notify/intf/server.intf", "{\"head\":{\"sysCode\":\"1005\",\"transTime\":\"090953\",\"transType\":\"T\",\"transDate\":\"20180115\",\"transCode\":\"IP03\",\"transSerialNo\":\"100518011503400491515432\"},\"body\":{\"fileName\":\"1000_200_20180115144100_1.txt\",\"sts\":\"S\",\"resCode\":\"0000\",\"remark\":\"\",\"resMsg\":\"等待业务回盘\"}}");
		System.out.println(httpResRtn.getStsCode()+"==="+httpResRtn.getValue());
	}

}
