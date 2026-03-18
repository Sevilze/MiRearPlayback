package com.mirearplayback;

interface ITaskService {
    void destroy() = 16777114;

    boolean executeShellCommand(String cmd) = 1;

    String executeShellCommandWithResult(String cmd) = 2;
}
