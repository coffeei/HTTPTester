# HTTPTester
test http api 

useage sample: data-in.txt
```
#headers
Accept: application/json, text/javascript, */*; q=0.01
Accept-Encoding: gzip, deflate
Accept-Language: zh-CN,zh;q=0.9
Cache-Control: no-cache
Connection: keep-alive
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36
X-Requested-With: XMLHttpRequest
#requestBody
_search=false&nd=1683287157886&rows=15&page=1&sidx=updateTime&sord=desc
#sysConfigs
durationSecd:10
threads:10
intervalSecd:1
logResult:true
#protocol
http://
#url
/http/domain/queryDomainList.do
#servers
localhost
192.168.0.1
```
input file:

put data-in.txt in the root directory in ide or specify as the first args when execute with command line

## sysConfigs
### durationSecd
the duration you want to execute
### threads
the threads count you want to execute
### intervalSecd
the  interval time in each thread 
### logResult
whether log the execute result,use true or false

