package com.chinajey.dwork.modules.standar_interface.material.form;

import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.Relation;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ExternalMaterialForm extends CodeForm {

    @NotBlank(message = "物料名称不能为空")
    private String name;

    @NotBlank(message = "物料类别编码不能为空")
    private String materialClassificationCode;

    @NotBlank(message = "默认仓库编码不能为空")
    private String defaultWarehouseCode;

    private String defaultWarehouseName;

    private String type;

    private String ledgerClassificationCode;

    private String ledgerClassificationName;

    // 状态
    private Boolean status=Boolean.TRUE;

    // 描述
    private String description;

    // 规格型号
    private String specifications;

    // 标准容量
    private BigDecimal standardCapacity;

    @NotBlank(message = "流转单位不能为空")
    private String flowUnitName;

    // 保质期
    private Integer sellByDate;

    // 单重
    private BigDecimal pieceWeight;

    // 单重单位
    private String pieceWeightUnitName;

    // 工位备品备件
    private String stationSpareParts;

    //拣配方式
    private String selectionMethod = "not";

    //是否启动序列号管理
    private Boolean isEnableSerial=Boolean.FALSE;

    /**
     * 对象关联
     */
    @Valid
    private List<Relation> relations;
}
