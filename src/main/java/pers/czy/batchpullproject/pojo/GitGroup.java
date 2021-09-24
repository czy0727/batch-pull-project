package pers.czy.batchpullproject.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author czy
 * @version $Id: GitGroup.java, v 0.1 2021-09-13 18:36 $$
 */
@Data
public class GitGroup {
    Long id;
    String name;
    String path;
    String description;
    @JsonProperty("parent_id")
    Long parentId;
}
