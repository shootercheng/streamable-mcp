package com.scd.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class CalculateService {

    @Tool(description = "计算2个数的和")
    public Integer calculateAdd(
            @ToolParam(description = "数据1") int num1,
            @ToolParam(description = "数据2") int num2
    ) {
        return num1 + num2;
    }
}
