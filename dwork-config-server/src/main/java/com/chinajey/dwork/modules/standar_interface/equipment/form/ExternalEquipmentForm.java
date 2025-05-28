package com.chinajey.dwork.modules.standar_interface.equipment.form;

import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

/**
 * 设备信息表单，用于外部系统接口的设备数据传输。
 *
 * @author erton.bi
 */
@Setter
@Getter
public class ExternalEquipmentForm extends CodeForm {

    /**
     * 设备名称，不能为空
     */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 设备型号，非必填字段
     */
    private String equipmentModel;

    /**
     * 设备类别编码，不能为空
     */
    @NotBlank(message = "设备类别编码不能为空")
    private String equipmentClassificationCode;

    /**
     * 设备状态，不能为空，且需要进行设备状态的校验（字典值）
     */
    @NotBlank(message = "设备状态不能为空")
    @CodeDict(value = "equipmentStatus", message = "设备状态错误")
    private String status;

    /**
     * 验收状态，非必填，默认为 "notAccepted"。验收状态需要校验（字典值）
     */
    @CodeDict(value = "acceptanceStatus", message = "验收状态错误")
    private String acceptanceStatus = "notAccepted";

    /**
     * 出厂编码，非必填
     */
    private String exFactoryCode;

    /**
     * 资产编码，非必填
     */
    private String propertyCode;

    /**
     * 资产名称，非必填
     */
    private String propertyName;

    /**
     * 保养类别，非必填，需要校验（字典值）
     */
    @CodeDict(value = "maintenanceType", message = "保养类别错误")
    private String maintenanceType;

    /**
     * 制造商，非必填
     */
    private String manufacturer;

    /**
     * 设备组名称，非必填
     */
    private String equipmentGroupCode;

    /**
     * 设备组编码，非必填
     */
    private String equipmentGroupName;

    /**
     * 购买日期，非必填，时间戳格式
     */
    private Long purchasingDate;

    /**
     * 验收日期，非必填，时间戳格式
     */
    private Long acceptanceDate;

    /**
     * 机台号，非必填
     */
    private String machineNumber;

    /**
     * 品牌，非必填
     */
    private String brand;

    /**
     * 使用年限（月），非必填，必须是正整数或0
     */
    @PositiveOrZero(message = "使用年限（月）必须是正整数数或0")
    private Integer serviceLife;

    /**
     * 到期日期，非必填，时间戳格式
     */
    private Long dueDate;

    /**
     * 单位名称，非必填
     */
    private String unitName;

    /**
     * 责任人编码，非必填
     */
    private String responsibleUserCode;

    /**
     * 监督人编码，非必填
     */
    private String superviseUserCode;

    /**
     * 存放位置，非必填
     */
    private String locationCode;

    /**
     * 资产归属部门编码，非必填
     */
    private String departmentCode;

    /**
     * 是否停机，默认为 FALSE
     */
    private Boolean shutdown = Boolean.FALSE;
}