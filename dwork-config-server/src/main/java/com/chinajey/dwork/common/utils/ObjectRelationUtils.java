package com.chinajey.dwork.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.dwork.common.form.BindForm;
import com.tengnat.dwork.common.cache.CacheBusiness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

public class ObjectRelationUtils {

    /**
     * 注意：调用前，先看代码。
     * 处理对象的关联关系
     * 处理前会将对象原有的绑定关系清除后重新绑定
     *
     * @param bmfObject        对象
     * @param bindingResources 对象绑定的资源 - 如果为空，会清掉绑定关系
     */
    public static void bind(BmfObject bmfObject, List<BindForm> bindingResources) {
        if (bindingResources == null) {
            bindingResources = new ArrayList<>();
        }
        // TODO 待优化，需要精准判断哪些需要删除，哪些需要新增，而不是一股脑的先删后增

        BmfService bmfService = SpringUtils.getBean(BmfService.class);
        JdbcTemplate jdbcTemplate = SpringUtils.getBean(JdbcTemplate.class);
        // 删除原有关系 - 包括正向与方向
        Object[] objects = new Object[]{CacheBusiness.getCacheResourceId(bmfObject.getBmfClassName()), bmfObject.getString("code")};
        jdbcTemplate.update("delete from dwk_resource_binding where resource_id = ? and resource_code = ?", objects);
        jdbcTemplate.update("delete from dwk_resource_binding where binding_resource_id = ? and binding_resource_code = ?", objects);

        for (BindForm bindingResource : bindingResources) {
            // 保存正向关系
            JSONObject forward = new JSONObject();
            forward.put("resource", CacheBusiness.getCacheResourceId(bmfObject.getBmfClassName()));
            forward.put("resourceCode", bmfObject.getString("code"));
            forward.put("resourceName", bmfObject.getString("name"));
            forward.put("bindingResource", CacheBusiness.getCacheResourceId(bindingResource.getType()));
            forward.put("bindingResourceCode", bindingResource.getCode());
            forward.put("bindingResourceName", bindingResource.getName());
            forward.put("remark", bindingResource.getRemark());
            BmfObject forwardBmfObject = BmfUtils.genericFromJsonExt(forward, "resourceBinding");
            bmfService.saveOrUpdate(forwardBmfObject);

            // 保存反向关系
            JSONObject reverse = new JSONObject();
            reverse.put("resource", CacheBusiness.getCacheResourceId(bindingResource.getType()));
            reverse.put("resourceCode", bindingResource.getCode());
            reverse.put("resourceName", bindingResource.getName());
            reverse.put("bindingResource", CacheBusiness.getCacheResourceId(bmfObject.getBmfClassName()));
            reverse.put("bindingResourceCode", bmfObject.getString("code"));
            reverse.put("bindingResourceName", bmfObject.getString("name"));
            BmfObject reverseBmfObject = BmfUtils.genericFromJsonExt(reverse, "resourceBinding");
            bmfService.saveOrUpdate(reverseBmfObject);
        }
    }
}
