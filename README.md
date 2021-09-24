### 通过gitlab Api自动下载gitLab上的所有项目

#### 使用方式
- 1、修改application.properties中的git.gitlabUrl为自己的gitlab地址
- 2、修改application.properties中的git.privateToken为自己的personalAccessTokens，步骤如下：  
&ensp;&ensp;在gitLab上登录 ->  
&ensp;&ensp;点击右上角个人头像 ->  
&ensp;&ensp;settings ->  
&ensp;&ensp;左侧菜单 Access Tokens ->  
&ensp;&ensp;输入Name ->  
&ensp;&ensp;选择Expires at ->  
&ensp;&ensp;在Scopes中勾选所有选项 ->  
&ensp;&ensp;最后点击Create personal access token
- 3、修改application.properties中的git.projectDir为自己存放项目的磁盘地址
- 4、修改application.properties中的git.branch为需要clone或者pull的分支

#### 实现逻辑

- 1、通过API获取分组列表

- 2、遍历分组列表

- 3、通过指定分组名称获取项目列表

- 4、遍历项目列表

- 5、克隆指定分支的项目到指定目录