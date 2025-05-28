package com.chinajey.dwork.common;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.holder.ThreadLocalHolder;
import com.chinajey.application.common.holder.UserAuthDto;
import com.chinajey.dwork.common.enums.InitiateType;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public class FillUtils {

    public static UserAuthDto.Resource getUserInfo() {
        return Optional.ofNullable(ThreadLocalHolder.getLoginInfo()).map(UserAuthDto.LoginInfo::getResource).orElse(null);
    }

    public static void fillOperator(JSONObject jsonObject) {
        UserAuthDto.Resource userInfo = getUserInfo();
        if (userInfo != null) {
            jsonObject.put("operatorCode", userInfo.getResourceCode());
            jsonObject.put("operatorName", userInfo.getResourceName());
            jsonObject.put("operatorTime", new Date());
        }
    }

    public static void fillComFields(List<BmfObject> initiates, JSONObject generate, InitiateType type) {
        generate.put("sourceSystem", initiates.get(0).getString("sourceSystem"));
        generate.put("externalDocumentType", initiates.get(0).getString("externalDocumentType"));
        String originExternal = generate.getString("externalDocumentCode");
        String newExternal = AssistantUtils.getSplitValues(initiates, "externalDocumentCode");
        generate.put("externalDocumentCode", AssistantUtils.getSplitValues(originExternal, newExternal, ","));
        generate.put("sourceDocumentType", initiates.get(0).getString("sourceDocumentType"));
        String originSource = generate.getString("sourceDocumentCode");
        String newSource = AssistantUtils.getSplitValues(initiates, "sourceDocumentCode");
        generate.put("sourceDocumentCode", AssistantUtils.getSplitValues(originSource, newSource, ","));
        if (type == InitiateType.PC_ORDER) {
            generate.put("preDocumentType", initiates.get(0).getBmfClassName());
            String originPre = generate.getString("preDocumentCode");
            String newPre = AssistantUtils.getSplitValues(initiates, "code");
            generate.put("preDocumentCode", AssistantUtils.getSplitValues(originPre, newPre, ","));
        } else if (type == InitiateType.APP) {
            generate.put("preDocumentType", initiates.get(0).getString("preDocumentType"));
            String originPre = generate.getString("preDocumentCode");
            String newPre = AssistantUtils.getSplitValues(initiates, "preDocumentCode");
            generate.put("preDocumentCode", AssistantUtils.getSplitValues(originPre, newPre, ","));
        }
        generate.put("orderBusinessType", initiates.get(0).getString("orderBusinessType"));
    }

    public static void extendComFields(JSONObject source, JSONObject target) {
        target.put("sourceSystem", source.getString("sourceSystem"));
        target.put("orderBusinessType", source.getString("orderBusinessType"));
        extendComDocFields(source, target);
    }

    public static void extendComDocFields(JSONObject source, JSONObject target) {
        target.put("externalDocumentType", source.getString("externalDocumentType"));
        target.put("externalDocumentCode", source.getString("externalDocumentCode"));
        target.put("sourceDocumentType", source.getString("sourceDocumentType"));
        target.put("sourceDocumentCode", source.getString("sourceDocumentCode"));
        target.put("preDocumentType", source.getString("preDocumentType"));
        target.put("preDocumentCode", source.getString("preDocumentCode"));
    }
}
