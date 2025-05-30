package com.chinajey.dwork.modules.commonInterface.from;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.EnumValue;
import com.chinajey.application.common.exception.BusinessException;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class MaintenanceApplySyncForm {

    //private String code;

    private Date oaTime = new Date();

    private Date issueTime = new Date();

    private String sourceType = "OA";

    @NotBlank(message = "来源编码不能为空")
    private String sourceCode;

    @NotBlank(message = "申请人编码不能为空")
    private String applicantCode;

    //需要填充
    private String applicantName;

    private String status = "approved";

    @NotBlank(message = "周转箱编码不能为空")
    private String passBoxCode;

    /**
     * 此处的台账编码是台账主数据的来源编码
     */
    //@NotBlank(message = "台账编码不能为空")
    //需要填充
    private String ledgerCode;

    //需要填充
    private String ledgerName;

    //需要填充
    private BigDecimal life;

    //需要填充
    private String specifications;

    @NotBlank(message = "维修类型不能为空")
    @EnumValue(strValues = {"internal", "external"}, message = "参数[type]必须为指定值")
    private String type;

    private String remark;

    private String factoryCode;

    //需要填充
    private String factoryName;

    private String groupCode;

    //需要填充
    private String groupName;

/*    @NotBlank(message = "器具类型不能为空")
    @EnumValue(strValues = {"knife", "fixture", "mold", "measuringTool", "jig", "rack"}, message = "参数[toolType]必须为指定值")
    private String toolType;*/

    //需要填充
    private BmfObject department;

    @NotBlank(message = "故障原因编码不能为空")
    private String failureCauseCode;

    //需要填充
    private BmfObject failureCause;

    @NotBlank(message = "维修建议不能为空")
    private String suggest;

    public void validate(){
        if ("internal".equals(type)){
            if (StringUtils.isBlank(groupCode)){
                throw new BusinessException("维修班组不能为空");
            }
            factoryCode = null;
            factoryName = null;
        }

        if ("external".equals(type)){
            if (StringUtils.isBlank(factoryCode)){
                throw new BusinessException("维修厂家不能为空");
            }
            groupCode = null;
            groupName = null;
        }
    }
}
