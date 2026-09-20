import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig, msalInstance } from './app/app.config';
import { App } from './app/app';

msalInstance.initialize()
  .then(() => msalInstance.handleRedirectPromise())
  .then((result) => {
    if (result?.account) {
      msalInstance.setActiveAccount(result.account);
    }

    return bootstrapApplication(App, appConfig);
  })
  .catch((err) => console.error(err));
