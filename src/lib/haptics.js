// רטט עדין בטלפון — משוב מישושי לתשובות (בדפדפן: navigator.vibrate אם קיים).

import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics'

export function hapticCorrect() {
  Haptics.impact({ style: ImpactStyle.Light }).catch(() => {})
}

export function hapticWrong() {
  Haptics.notification({ type: NotificationType.Error }).catch(() => {})
}

export function hapticSuccess() {
  Haptics.notification({ type: NotificationType.Success }).catch(() => {})
}
