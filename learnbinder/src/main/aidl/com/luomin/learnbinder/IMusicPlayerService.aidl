// IMusicPlayerService.aidl
package com.luomin.learnbinder;

// Declare any non-default types here with import statements

interface IMusicPlayerService {

    boolean start(String audioPath);
    void stop();
}
