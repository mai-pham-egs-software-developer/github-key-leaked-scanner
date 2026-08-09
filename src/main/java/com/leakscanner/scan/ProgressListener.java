package com.leakscanner.scan;

@FunctionalInterface
public interface ProgressListener {
  void onProgress(long pushEvents, long pushEventsSampled, long patchesFetched, long fetchErrors);
}
