package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateNovelDTO {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 100)
    private String authorName;

    @NotBlank
    @Size(max = 50)
    private String category;

    @Size(max = 500)
    private String coverUrl;

    @Size(max = 2000)
    private String summary;

    /** 1=连载中 2=已完结 */
    @NotNull
    private Integer status;
}
