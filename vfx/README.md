# אפקטים בסגנון דוקטור סטריינג' לסרטון טלפון

הצינור מוסיף לסרטון אמיתי מגן מנדלה על כף היד, פורטל של ניצוצות שנפתח **מאחורי** האדם ומראה עולם אחר, ניצוצות, סגירת פורטל על כל הפריים ועיצוב סאונד.

## איך זה עובד
1. `analyze.py` מריץ MediaPipe על כל פריים. הוא מזהה את הידיים ואת התנוחה ומפיק מסכת אדם.
2. `render.py` מבצע צביעה קולנועית, מוסיף מנדלה, פורטל וניצוצות לפי מיקום הידיים, ממסך את הפורטל מאחורי האדם, ומוסיף bloom ו־rim light.
3. `sfx.py` מסנתז אפקטי סאונד בתזמון של האפקטים ומערבב אותם עם הסאונד המקורי.

## הרצה
```bash
pip install "mediapipe==0.10.14" opencv-python-headless scipy numpy imageio-ffmpeg
python3 analyze.py SRC.mp4 WORK
python3 render.py SRC.mp4 WORK WORK/video_fx.mp4
python3 sfx.py SRC.mp4 WORK/sfx.wav
ffmpeg -i WORK/video_fx.mp4 -i WORK/sfx.wav -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest out.mp4
```
התזמונים (`T_SHIELD_IN`, `T_TRACE0`, `T_IRIS0` וכו') ומיקום הפורטל (`C_PORTAL`, `R_PORTAL`) מוגדרים בראש קטע ה־timeline ב־`render.py`.
