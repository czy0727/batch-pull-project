package pers.czy.batchpullproject.service;

import pers.czy.batchpullproject.constant.Constant;
import pers.czy.batchpullproject.pojo.GitBranch;
import pers.czy.batchpullproject.pojo.GitGroup;
import pers.czy.batchpullproject.pojo.GitProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 通过gitlab Api自动下载gitLab上的所有项目
 *
 * @author czy
 * @version $Id: GitlabProjectCloneService.java, v 0.1 2021-09-13 18:36 $$
 */
@Service
@Slf4j
public class GitlabProjectCloneService {
    private ExecutorService executorService = Executors.newFixedThreadPool(16);
    private List<Future<?>> futureList = new ArrayList<>();

    @Value("${git.gitlabUrl}")
    private String gitlabUrl;

    @Value("${git.privateToken}")
    private String privateToken;

    @Value("${git.projectDir}")
    private String projectDir;

    @Value("${git.branch}")
    private String branch;

    @Autowired
    RestTemplate restTemplate;

    @PostConstruct
    public void start() {
        File execDir = this.getProjectDir(projectDir);
        if (ObjectUtils.isEmpty(execDir)) {
            return;
        }

        log.info("[start get gitlab projects]~~~~~~");
        //获取分组
        List<GitGroup> groups = this.getGroups();
        log.info("{}", groups);

        //获得分组下面的项目，更新
        groups.forEach(group ->
                futureList.add(executorService.submit(() -> {
                            List<GitProject> projects = getProjectsByGroup(group.getPath());
                            for (GitProject project : projects) {
                                if (!canCloneOrPull(project)) {
                                    continue;
                                }

                                String projectAbsolutePath = projectDir + group.getPath() + "/" + project.getName().toLowerCase();
                                File projectAbsoluteDir = new File(projectAbsolutePath);

                                futureList.add(executorService.submit(() -> {
                                    try {
                                        //如果文件夹已存在则pull，不存在则clone
                                        if (projectAbsoluteDir.exists()) {
                                            pullProject(branch, projectAbsoluteDir, projectAbsolutePath);
                                        } else {
                                            cloneProject(branch, project, execDir);
                                        }
                                    } catch (Exception ex) {
                                        log.error("[run] failed:", ex);
                                    }
                                }));
                            }
                        })
                ));
        try {
            exit();
        } catch (Exception e) {
            log.error("shutdown application failed!!!");
            e.printStackTrace();
        }
    }

    /**
     * 获取指定分组下的项目
     *
     * @param group 分组
     * @return 分组下的项目
     */
    private List<GitProject> getProjectsByGroup(String group) {
        String url = gitlabUrl + "/api/v4/groups/{group}/projects?per_page={per_page}&private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("group", group);
        uriVariables.put(Constant.PER_PAGE, Constant.SIZE);
        uriVariables.put(Constant.PRIVATE_TOKEN, privateToken);
        HttpHeaders headers = this.getHeaders();

        HttpEntity<Object> entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitProject>> responseType = new ParameterizedTypeReference<List<GitProject>>() {
        };

        ResponseEntity<List<GitProject>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }

        return Collections.emptyList();
    }

    /**
     * 获取分组列表
     *
     * @return 分组集合
     */
    private List<GitGroup> getGroups() {
        String url = gitlabUrl + "/api/v4/groups?private_token={private_token}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put(Constant.PRIVATE_TOKEN, privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitGroup>> responseType = new ParameterizedTypeReference<List<GitGroup>>() {
        };

        ResponseEntity<List<GitGroup>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);
        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return this.getRealPath(responseEntity.getBody());
        }
        return Collections.emptyList();
    }

    /**
     * 获取指定项目的分支列表
     * https://docs.gitlab.com/ee/api/branches.html#branches-api
     *
     * @param projectId 项目ID
     * @return 项目的分支集合
     */
    private List<GitBranch> getBranches(Long projectId) {
        String url = gitlabUrl + "/api/v4/projects/{projectId}/repository/branches?private_token={privateToken}";
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("projectId", projectId);
        uriVariables.put("privateToken", privateToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(headers);
        ParameterizedTypeReference<List<GitBranch>> responseType = new ParameterizedTypeReference<List<GitBranch>>() {
        };
        ResponseEntity<List<GitBranch>> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, responseType, uriVariables);

        if (HttpStatus.OK == responseEntity.getStatusCode()) {
            return responseEntity.getBody();
        }
        return Collections.emptyList();
    }

    private void cloneProject(String branchName, GitProject gitProject, File execDir) {
        String command = String.format("git clone -b %s %s %s", branchName, gitProject.getHttpUrlToRepo(), gitProject.getPathWithNamespace());
        log.info("[GitlabProjectCloneService][clone] start exec command : {}", command);
        this.executeCommand(command, null, execDir);
    }

    private void pullProject(String branchName, File projectAbsoluteDir, String projectAbsolutePath) {
        String command = String.format("git -C %s pull origin %s", projectAbsolutePath, branchName);
        log.info("[GitlabProjectCloneService][pull] start exec command : {}", command);
        this.executeCommand(command, null, projectAbsoluteDir);
    }

    /**
     * 更新每个分组的path，特别是子分组，否则会出现404
     *
     * @param gitGroupList 需要拼接path的所有分组
     * @return 拼接好path的分组
     */
    private List<GitGroup> getRealPath(List<GitGroup> gitGroupList) {
        if (CollectionUtils.isEmpty(gitGroupList)) {
            return Collections.emptyList();
        }
        gitGroupList.forEach(gitGroup -> gitGroup.setPath(recursion(gitGroupList, gitGroup)));
        return gitGroupList;
    }

    /**
     * 拼接每个分组的path
     *
     * @param gitGroupList  所有的分组
     * @param childGitGroup 需要拼接path的分组
     * @return path
     */
    private String recursion(List<GitGroup> gitGroupList, GitGroup childGitGroup) {
        if (ObjectUtils.isEmpty(childGitGroup.getParentId())) {
            return childGitGroup.getPath();
        }
        //获得当前分组的父分组，必定有一个
        List<GitGroup> collect = gitGroupList.stream().filter(gitGroup -> ObjectUtils.nullSafeEquals(gitGroup.getId(), childGitGroup.getParentId())).collect(Collectors.toList());
        return recursion(gitGroupList, collect.get(0)) + "/" + childGitGroup.getPath();
    }

    /**
     * 获取请求头信息
     *
     * @return 请求头
     */
    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void executeCommand(String command, String[] envp, File dir) {
        try {
            //执行脚本命令
            Process exec = Runtime.getRuntime().exec(command, envp, dir);
            exec.waitFor();
            String successResult = StreamUtils.copyToString(exec.getInputStream(), StandardCharsets.UTF_8);
            String errorResult = StreamUtils.copyToString(exec.getErrorStream(), StandardCharsets.UTF_8);
            log.info("successResult: {}", successResult);
            log.info("errorResult: {}", errorResult);
            log.info(Constant.DELIMITER);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取项目要保存的目录
     *
     * @param diskPath 磁盘路径
     * @return 目录
     */
    private File getProjectDir(String diskPath) {
        File execDir = new File(diskPath);
        if (!execDir.exists()) {
            boolean mkdirResult = execDir.mkdirs();
            if (!mkdirResult) {
                log.error("创建文件夹[{}]失败", execDir.getPath());
                return null;
            }
        }
        return execDir;
    }

    /**
     * 能否进行clone或者pull
     *
     * @param project 要clone或者pull的项目
     * @return true能，false不能
     */
    private boolean canCloneOrPull(GitProject project) {
        List<GitBranch> branchList = getBranches(project.getId());
        if (CollectionUtils.isEmpty(branchList)) {
            log.info("There are no branch in project [{}]", project.getName());
            log.info(Constant.DELIMITER);
            return false;
        }
        List<String> branchNameList = new ArrayList<>();
        branchList.forEach(temp -> branchNameList.add(temp.getName()));
        if (!branchNameList.contains(branch)) {
            log.info("branch [{}] do not exist in project [{}]", branch, project.getName());
            log.info(Constant.DELIMITER);
            return false;
        }
        return true;
    }

    /**
     * 退出应用程序
     *
     * @throws InterruptedException
     */
    private void exit() throws InterruptedException {
        while (true) {
            boolean finished = true;
            for (Future<?> future : futureList) {
                if (!future.isDone()) {
                    finished = false;
                    break;
                }
            }
            if (finished) {
                break;
            }
            log.info("还有任务未执行完毕，请等待...");
            Thread.sleep(1000);
        }
        log.info("[end get gitlab projects]~~~~~~");
        System.exit(0);
    }
}
