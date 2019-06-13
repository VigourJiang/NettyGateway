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
package com.newlandframework.gateway.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.newlandframework.gateway.commons.GatewayAttribute;
import com.newlandframework.gateway.commons.HttpClientUtils;
import com.newlandframework.gateway.commons.RouteAttribute;
import com.newlandframework.gateway.commons.RoutingLoader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.Signal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.io.DefaultHttpResponseParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.LineParser;
import org.apache.http.util.EntityUtils;
import org.springframework.util.StringUtils;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.newlandframework.gateway.commons.GatewayOptions.*;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author tangjie<https://github.com/tang-jie>
 * @filename:GatewayServerHandler.java
 * @description:GatewayServerHandler功能模块
 * @blogs http://www.cnblogs.com/jietang/
 * @since 2018/4/18
 */
public class GatewayServerHandler extends SimpleChannelInboundHandler<Object> {
    private HttpRequest request;
    private ByteArrayOutputStream buffer = null;
    private String url = "";
    private String uri = "";
    private HttpClientUtils.ParsedHttpResp respone;
    private GlobalEventExecutor executor = GlobalEventExecutor.INSTANCE;
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;

            if (HttpUtil.is100ContinueExpected(request)) {
                notify100Continue(ctx);
            }

            buffer = new ByteArrayOutputStream();
            uri = request.uri().substring(1);
        }

        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf content = httpContent.content();
            if (content.isReadable()) {
                byte[] bytes = new byte[content.readableBytes()];
                content.getBytes(content.readerIndex(), bytes);
                try {
                    buffer.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (msg instanceof LastHttpContent) {
                LastHttpContent trace = (LastHttpContent) msg;

                url = matchUrl();

                boolean direct = false;
                if(uri.startsWith("uiconfig")) {
                    try {
                        buffer.write(getUiConfig().getBytes("utf-8"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    direct = true;
                } else if(uri.startsWith("menuconfig")) {
                    try {
                        buffer.write(getMenuConfig().getBytes("utf-8"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    direct = true;
                }

                if(direct) {
                    writeRawResponse(buffer.toByteArray(), trace, ctx);
                }
                else if(request.method().name().equals("GET")) {
                    Future<HttpClientUtils.ParsedHttpResp> future =
                            executor.submit(new Callable<HttpClientUtils.ParsedHttpResp>() {
                                @Override
                                public HttpClientUtils.ParsedHttpResp call() {
                                    try {
                                        return HttpClientUtils.get("http://localhost:9092/" + uri, request);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        throw new RuntimeException("");
                                    }
                                }
                            });

                    future.addListener(new FutureListener<HttpClientUtils.ParsedHttpResp>() {
                        @Override
                        public void operationComplete(Future<HttpClientUtils.ParsedHttpResp> future) throws Exception {
                            if (future.isSuccess()) {
                                respone = ((HttpClientUtils.ParsedHttpResp)
                                        future.get(GATEWAY_OPTION_HTTP_POST, TimeUnit.MILLISECONDS));
                            } else {
                                respone = null;
                            }
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                        writeResponse(respone, future.isSuccess() ? trace : null, ctx);
                        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    Future<HttpClientUtils.ParsedHttpResp> future =
                            executor.submit(new Callable<HttpClientUtils.ParsedHttpResp>() {
                        @Override
                        public HttpClientUtils.ParsedHttpResp call() {
                            try {
                                return HttpClientUtils.postJson(url + uri.replace("api/", "/"), request, buffer.toByteArray());
                            } catch (Exception e) {
                                e.printStackTrace();
                                throw new RuntimeException("");
                            }
                        }
                    });

                    future.addListener(new FutureListener<HttpClientUtils.ParsedHttpResp>() {
                        @Override
                        public void operationComplete(Future<HttpClientUtils.ParsedHttpResp> future) throws Exception {
                            if (future.isSuccess()) {
                                respone = ((HttpClientUtils.ParsedHttpResp)
                                        future.get(GATEWAY_OPTION_HTTP_POST, TimeUnit.MILLISECONDS));
                            } else {
                                respone = null;
                            }
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                        writeResponse(respone, future.isSuccess() ? trace : null, ctx);
                        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private String matchUrl() {
        if(uri.startsWith("api/authority")) {
            return "http://192.168.140.100:8080";
        } else if(uri.startsWith("api/topology")) {
            return "http://127.0.0.1:9010";

        } else if(uri.startsWith("api/monitor")) {
            return "http://127.0.0.1:9011";
        }
        return "";
    }

    private void writeRawResponse(byte[] json,
                               HttpObject current,
                               ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1,
                    OK,
                    Unpooled.wrappedBuffer(json));


            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Credentials", "true");
            response.headers().set("Content-Type", "application/json");
            response.headers().set("Content-Length", json.length);
            ctx.writeAndFlush(response);
    }

    private void simpleGet(){

    }

    private void writeResponse(HttpClientUtils.ParsedHttpResp respone,
                               HttpObject current,
                               ChannelHandlerContext ctx) {
        if (respone != null) {
//            boolean keepAlive = HttpUtil.isKeepAlive(request);


            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1,
                    current == null ? OK : current.decoderResult().isSuccess() ? OK : BAD_REQUEST,
                    Unpooled.wrappedBuffer(respone.body));

            if(respone.headers != null) {
                for (Header header : respone.headers) {
                    response.headers().set(header.getName(), header.getValue());
                }
            }
            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Credentials", "true");

            if(uri.endsWith("/user/login")) {
                String jsonResp = null;
                try {
                    jsonResp = new String(respone.body, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                JSONObject map = JSON.parseObject(jsonResp);
                if(map.getInteger("ret_code") == 0) {
                    response.headers().set("Set-Cookie",
                            "mycookie=cookie_value; Path=/; httpOnly=true");
                } else {
                    response.headers().set("Set-Cookie",
                            "mycookie=; Path=/; httpOnly=true");
                }
            } else {
            }

//            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resStr.length());


            /*
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            */
            ctx.write(response);
        }
    }

    private static void notify100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    private String getUiConfig() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            engine.eval(new FileReader("config.js"));
            Invocable invocable = (Invocable) engine;

            Object result = invocable.invokeFunction("getUiConfig");
            System.out.println(result);
            return result.toString();
        } catch (ScriptException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getMenuConfig() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            engine.eval(new FileReader("config.js"));
            Invocable invocable = (Invocable) engine;

            Object result = invocable.invokeFunction("getMenuConfig");
            System.out.println(result);
            return result.toString();
        } catch (ScriptException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return "";
    }
}

