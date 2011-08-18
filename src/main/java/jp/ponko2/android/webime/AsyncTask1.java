package jp.ponko2.android.webime;

import android.os.AsyncTask;

public abstract class AsyncTask1<T1, T2, T3> extends AsyncTask<T1, T2, T3> {
   protected T3 doInBackground (T1 ... params) {
      return doInBackground(params.length > 0 ? params[0] : null);
   }

   abstract protected T3 doInBackground (T1 p);
}
