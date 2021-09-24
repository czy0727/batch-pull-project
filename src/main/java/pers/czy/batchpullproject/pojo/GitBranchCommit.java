package pers.czy.batchpullproject.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * @author czy
 * @version $Id: GitBranchCommit.java, v 0.1 2021-09-13 18:36 $$
 */
@Data
public class GitBranchCommit {
    String id;
    @JsonProperty(value = "committed_date")
    Date committedDate;
}
