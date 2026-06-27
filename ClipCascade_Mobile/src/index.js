/**
 * ClipCascade - A seamless clipboard syncing utility
 * Repository: https://github.com/Sathvik-Rao/ClipCascade
 *
 * Author: Sathvik Rao Poladi
 * License: GPL-3.0
 */

import {AppRegistry} from 'react-native';
import AppRoot from './AppRoot';
import {name as appName} from './app.json';
import {registerRecoveryListener} from './RecoveryListener';

registerRecoveryListener();

AppRegistry.registerComponent(appName, () => AppRoot);
AppRegistry.registerHeadlessTask('Restart', () => require('./HeadlessTask'));
