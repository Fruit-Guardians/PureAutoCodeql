import java

from MethodAccess ma
where ma.getMethod().hasName("readLine")
select ma  # 缺少分号
