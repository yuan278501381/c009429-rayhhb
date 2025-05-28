package com.chinajey.dwork.modules.standar_interface.fixture.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Date;


@Data
public class ExternalFixtureSyncForm extends CodeForm {

    private String checkStatus;
    //夹具台账编码
    private String name;

    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @NotBlank(message = "夹具类别编码不能为空")
    private String fixtureClassificationCode;

    private String buGroup;
    private String fixedAssetCode;
    private String brand;
    @NotNull(message = "责任部门id不能为空")
    private Long responsibleDeptId;
    @NotBlank(message = "责任人编码不能为空")
    private String responsibleUserCode;
    private String customerUserCode;

    //internal   external
    @NotBlank(message = "存放位置类别不能为空")
    private String locationClassification;
    @NotBlank(message = "位置编码不能为空")
    private String locationCode;
    private String warehouseCode;
    private Date storageTime;
    private Date oaCreateTime;

    //'warehouse', '在仓库
    //'inUse', '使用中'
    private String status;
    private String remark;
    //ERP编码
    private String erpCode;
    //周期单位 cycleUnitEnum
    private String cycleUnit;
    //校验周期
    private Integer checkCycle;
    //制造商
    private String manufacturerCode;

    @Max(value = 100, message = "必须小于等于100")
    @PositiveOrZero(message = "寿命必须是正数或0")
    private BigDecimal life;
    //生产日期
    private Date productionDate;

    private String drawingNumber;
    private String processNo;
    private String procedureNo;
    private String sourceCode;

}
