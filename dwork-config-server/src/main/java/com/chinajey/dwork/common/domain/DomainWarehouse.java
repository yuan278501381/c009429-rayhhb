package com.chinajey.dwork.common.domain;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.tengnat.dwork.modules.basic_data.domain.DomainBindResource;
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ObjectResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class DomainWarehouse {
    private final BmfService bmfService;

    public DomainWarehouse() {
        this.bmfService = SpringUtils.getBean(BmfService.class);
    }

    // 获取仓库信息
    public BmfObject getWarehouse(String warehouseCode) {
        BmfObject warehouse = bmfService.findByUnique("warehouse", "code", warehouseCode);
        if (warehouse == null) {
            throw new RuntimeException("仓库信息不存在");
        }
        return warehouse;
    }

    //判断仓库是否为电子仓
    public boolean isElectronicWarehouse(String warehouseCode) {
        BmfObject warehouse = getWarehouse(warehouseCode);
        return "electronicWarehouse".equals(warehouse.getString("ext_storage_type"));
    }

    //判断仓库是否为虚拟仓/第三方仓
    public boolean isVirtualWarehouse(String warehouseCode) {
        List<String> codes = Arrays.asList("3", "4", "5");
        BmfObject warehouse = getWarehouse(warehouseCode);
        BmfObject category = warehouse.getAndRefreshBmfObject("category");
        return codes.contains(category.getString("code"));
    }

    //判断仓库是否为线边仓
    public boolean isLineWarehouse(String warehouseCode) {
        BmfObject warehouse = getWarehouse(warehouseCode);
        BmfObject category = warehouse.getAndRefreshBmfObject("category");
        return "2".equals(category.getString("code"));
    }

    /**
     * 获取虚拟库位下的虚拟位置
     * 找到发出虚拟仓的虚拟位置传统手艺取第一个(默认只有一个)
     * * @param warehouseCode
     *
     * @return
     */
    public BmfObject getVirtualLocation(String warehouseCode) {
        if (!isVirtualWarehouse(warehouseCode)) {
            throw new BusinessException("仓库不是虚拟仓");
        }
        String locationCode = null;
        // 根据仓库查询对应的位置是否只有一个
        // 仓库-区域-位置 或者 仓库-位置 维度
        DomainBindResource domainBindResource = new DomainBindResource();
        List<ObjectResource> areaResources = domainBindResource.getBindResources("warehouse", warehouseCode, "area");
        if (!CollectionUtils.isEmpty(areaResources)) {
            if (areaResources.size() > 1) {
                throw new BusinessException("虚拟仓库绑定区域只能有一个" + warehouseCode);
            }
            ObjectResource areaResource = areaResources.get(0);
            List<ObjectResource> locationResources = domainBindResource.getBindResources("area", areaResource.getCode(), "location");
            if (org.apache.commons.collections.CollectionUtils.isEmpty(locationResources)) {
                throw new BusinessException("虚拟仓库绑定区域绑定位置不能为空" + warehouseCode);
            }
            if (locationResources.size() > 1) {
                throw new BusinessException("供应商：仓库绑定区域绑定位置只能有一个" + warehouseCode);
            }
            locationCode = locationResources.get(0).getCode();
        } else {
            List<ObjectResource> locationResources = domainBindResource.getBindResources("warehouse", warehouseCode, "location");
            if (CollectionUtils.isEmpty(locationResources)) {
                throw new BusinessException("虚拟仓库绑定位置不能为空" + warehouseCode);
            }
            if (locationResources.size() > 1) {
                throw new BusinessException("虚拟仓库绑定位置只能有一个" + warehouseCode);
            }
        }
        BmfObject location = bmfService.findByUnique("location", "code", locationCode);
        if (location == null) {
            throw new BusinessException(String.format("虚拟仓%s没有找到绑定位置%s", warehouseCode, locationCode));
        }
        return location;
    }

    //是否上架库位(智能亮灯或电子仓)
    public boolean isShelve(String warehouseCode) {
        return isShelve(getWarehouse(warehouseCode));
    }

    //是否上架库位(智能亮灯或电子仓)
    public boolean isShelve(BmfObject warehouse) {
        if (warehouse == null) return false;
        String storageType = warehouse.getString("ext_storage_type");
        return StringUtils.equals(storageType, "IntelligentLighting") || StringUtils.equals(storageType, "electronicWarehouse");
    }

}
