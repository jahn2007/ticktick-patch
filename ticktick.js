/******************************

脚本功能：TickTick解锁
原脚本作者：彭于晏💞
修改：A Mirror
更新时间：2026-05-05
使用声明：⚠️此脚本仅供学习与交流，请勿转载与贩卖！⚠️⚠️⚠️

*******************************

[rewrite_local]

^https:\/\/ticktick\.com\/api\/v2\/user\/status url script-response-body https://raw.githubusercontent.com/jahn2007/ticktick-patch/quanx/ticktick.js

[mitm] 

hostname = ticktick.com

*******************************/
var body = $response.body;
var obj = JSON.parse(body);

obj.proEndDate = "2099-01-01T00:00:00.000+0000";
obj.needSubscribe = false;
obj.pro = true;

body = JSON.stringify(obj);
$done(body);
