package com.example.mindmap.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonParser;
import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.okhttp.*;
import com.lark.oapi.service.bitable.v1.model.*;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.lark.oapi.service.docx.v1.model.*;
import com.lark.oapi.service.docx.v1.model.TextRun;
import com.lark.oapi.service.drive.v1.model.*;
import com.lark.oapi.service.drive.v1.model.File;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;


@Slf4j
public class ThirdPartyAPITool {

    public static final String APP_ID = "cli_a4f2cf98cea39013";

    public static final String APP_SECRET = "6rIHwbVsIO6T4b44PZbqogbFF04VNRFi";
    public static String getDocContent(String docToken) {
        // 构建client
        Client client = Client.newBuilder(APP_ID, APP_SECRET).build();

        // 创建请求对象
        RawContentDocumentReq req = RawContentDocumentReq.newBuilder()
                .documentId(docToken)
                .lang(0)
                .build();

        // 发起请求
        try {
            RawContentDocumentResp resp = client.docx().v1().document().rawContent(req);

            // 处理服务端错误
            if (!resp.success()) {
                System.out.println(String.format("code:%s,msg:%s,reqId:%s, resp:%s",
                        resp.getCode(), resp.getMsg(), resp.getRequestId(), Jsons.createGSON(true, false).toJson(JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8)))));
                return "error";
            }


            String content = Jsons.DEFAULT.toJson(resp.getData());

            // 业务数据处理
            return content;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void sendMessage(String userId, String text, String idType) {
        try {
            // 构建client
            Client client = Client.newBuilder(APP_ID, APP_SECRET).build();

            // 创建请求对象
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType(idType)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(userId)
                            .msgType("text")
                            .content("{\"text\":\"" + text + "\"}")
                            .build())
                    .build();

            // 发起请求
            CreateMessageResp resp = client.im().message().create(req);

            // 处理服务端错误
            if (!resp.success()) {
                System.out.printf("code:%s,msg:%s,reqId:%s, resp:%s%n",
                        resp.getCode(), resp.getMsg(), resp.getRequestId(),
                        Jsons.createGSON(true, false).toJson(JsonParser
                                .parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8))));
                return;
            }

            // 业务数据处理
            System.out.println(Jsons.DEFAULT.toJson(resp.getData()));
        } catch (Exception e) {
            log.error("消息发送异常", e);
        }

    }
    /**
     * 查询飞书多维表格
     * @param
     * @throws Exception
     */
    public static AppTableRecord queryOneRecord(String appToken,String tableToken,String fieldName,String fieldvalue) {
        // 构建client
        Client client = Client.newBuilder(APP_ID, APP_SECRET).build();

        // 创建请求对象
        SearchAppTableRecordReq req = SearchAppTableRecordReq.newBuilder()
                .appToken(appToken)
                .tableId(tableToken)
                .pageSize(1)
                .searchAppTableRecordReqBody(SearchAppTableRecordReqBody.newBuilder()
                        .filter(FilterInfo.newBuilder()
                                .conjunction("and")
                                .conditions(new Condition[] {
                                        Condition.newBuilder()
                                                .fieldName(fieldName)
                                                .operator("is")
                                                .value(new String[] {
                                                        fieldvalue
                                                })
                                                .build()
                                })
                                .build())
                        .automaticFields(false)
                        .build())
                .build();

        // 发起请求
        try {
            SearchAppTableRecordResp resp = client.bitable().v1().appTableRecord().search(req);

            // 处理服务端错误
            if (!resp.success()) {
                System.out.println(String.format("code:%s,msg:%s,reqId:%s, resp:%s",
                        resp.getCode(), resp.getMsg(), resp.getRequestId(), new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8)));
                return null;
            }

            // 业务数据处理
            if(resp.getData().getItems().length>0){
                return resp.getData().getItems()[0];

            }else{
                return null;
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

}
