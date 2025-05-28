package com.chinajey.dwork.modules.standar_interface.measuringTool.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ExternalMeasuringToolForm extends CodeForm {

    private String checkStatus;
    //夹具台账编码
    private String name;

    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @NotBlank(message = "量检具类别编码不能为空")
    private String measuringToolClassificationCode;

    private String buGroup;
    private String fixedAssetCode;
    private String brand;
    private Long responsibleDeptId;
    @NotBlank(message = "责任人编码不能为空")
    private String responsibleUserCode;
    private String customerUserCode;

    //internal   external
    private String locationClassification;
    @NotBlank(message = "位置编码不能为空")
    private String locationCode;
    private String warehouseCode;
    private Date storageTime;
    private Date oaCreateTime;
    private Date checkDate; //校验日期
    private Date nextCheckDate; //下次校验日期
    private BigDecimal accuracy; //精度
    private String accuracyUnit; //精度单位
    private String sourceCode; //外部系统编码

    //'warehouse', '在仓库
    //'inUse', '使用中'
    private String status;
    private String remark;
    //ERP编码
    private String erpCode;
    //周期单位 cycleUnitEnum
    private String cycleUnit;
    //校验周期
    @NotNull(message = "校验周期不能为空")
    private Integer checkCycle;
    //校验方式
    @NotBlank(message = "校验方式不能为空")
    private String checkMethod;
    //制造商
    private String manufacturerCode;

    @Max(value = 100, message = "必须小于等于100")
    @PositiveOrZero(message = "寿命必须是正数或0")
    private BigDecimal life;
    //生产日期
    private Date productionDate;


}
