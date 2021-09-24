package pers.czy.batchpullproject.pojo;

import lombok.Data;

/**
 * @author czy
 * @version $Id: GitBranch.java, v 0.1 2021-09-13 18:36 $$
 */
@Data
public class GitBranch {
    String name;
    GitBranchCommit commit;
}
