//当前脚本文件保存的文件夹，使用文件夹区分不同类型的脚本
package groovy.demo

//我把基础类交给了 Spring容器管理。我要使用我的基础类，所以我要引入它
import com.chinajay.virgo.utils.SpringUtils
//我现在要写物流执行提交脚本，所以我要继承物流执行提交脚本的父类
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
//基础类的路径
import com.tengnat.dwork.modules.script.service.BasicGroovyService
//引入数据库表在脚本里面对象，看到BmfObject 就看到了表结构，里面就是表数据
import com.chinajay.virgo.bmf.obj.BmfObject

/**
 * 周转箱修改 Demo
 */
class NodePassBoxDemo extends NodeGroovyClass {
    //引入基础类
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    //执行这个脚本，就会运行的方法
    @Override
    Object runScript(BmfObject nodeData) {
        //界面上的周转箱编码 select ext_pass_box_code as passBoxCode from nodeData
        String passBoxCode=nodeData.getString("ext_pass_box_code")
        //位置名称
        String locationName=nodeData.getString("ext_location_name")

        //查询这个周转箱主数据
        //设置参数 WHERE code=passBoxCode and; {"code": "passBoxCode" }
        Map<String, Object> params=new HashMap<>()
        params.put("code",passBoxCode)
        params.put("name",locationName)

        //调用公共查询方法
        BmfObject passBox=basicGroovyService.findOne("passBox",params)

        //周转箱类别
        //Map<String, Object> params=new HashMap<>()
        //params.put("id", passBox.getString("id"))
        //BmfObject passBox=basicGroovyService.findOne("passBoxClassification",params)

        BmfObject passBoxClassification=passBox.getAndRefreshBmfObject("passBoxClassification")
        //是托盘
        if(passBoxClassification.getString("code") == "pallet"){
            //修改周转箱主数据的位置
            passBox.put("locationCode","WZ00001")
            passBox.put("locationName",locationName)

            //修改标准容量
            passBox.put("quantity",20)
            //调用公共修改方法
            basicGroovyService.updateByPrimaryKeySelective(passBox)

            //修改周转箱关联类别表的数据
            passBoxClassification.put("name","打大盘")
            //调用公共修改方法
            basicGroovyService.updateByPrimaryKeySelective(passBoxClassification)
        }
        return nodeData
    }
}