## 标准功能同步接口

* 类名都以：External开头

* 接口URI都以：/external/开头

* 接口入参不能使用JSONObject，必须使用自定义类继承ExtForm

* 需要在同步的表中添加外部标识字段，使用：externalDocumentCode，external_document_code
  例如：同步仓库类别，需要在仓库类别表增加外部标识字段：external_document_code，仓库类别的编码（code）还是使用编码规则生成，
  使用外部标识字段判断是新增还是更新

* 具体参考：/external/warehouse-category

