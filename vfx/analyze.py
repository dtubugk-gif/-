import subprocess, numpy as np, json, sys, mediapipe as mp, cv2
SRC = sys.argv[1]; OUT = sys.argv[2]
W, H = 1080, 1920
cmd = ['ffmpeg','-loglevel','error','-i',SRC,'-vf','fps=30','-f','rawvideo','-pix_fmt','rgb24','-']
p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
hands = mp.solutions.hands.Hands(static_image_mode=False, max_num_hands=2, model_complexity=1,
                                 min_detection_confidence=0.4, min_tracking_confidence=0.4)
seg = mp.solutions.selfie_segmentation.SelfieSegmentation(model_selection=0)
pose = mp.solutions.pose.Pose(static_image_mode=False, model_complexity=1)
res = []; masks = []
i = 0
while True:
    buf = p.stdout.read(W*H*3)
    if len(buf) < W*H*3: break
    f = np.frombuffer(buf, np.uint8).reshape(H, W, 3)
    r = hands.process(f)
    hs = []
    if r.multi_hand_landmarks:
        for lm, hd in zip(r.multi_hand_landmarks, r.multi_handedness):
            hs.append({'label': hd.classification[0].label, 'score': hd.classification[0].score,
                       'pts': [[l.x*W, l.y*H] for l in lm.landmark]})
    pr = pose.process(f)
    po = None
    if pr.pose_landmarks:
        po = [[l.x*W, l.y*H, l.visibility] for l in pr.pose_landmarks.landmark]
    m = seg.process(f).segmentation_mask
    masks.append(cv2.resize(m, (270, 480)).astype(np.float16))
    res.append({'i': i, 'hands': hs, 'pose': po})
    i += 1
json.dump(res, open(OUT + '/track.json', 'w'))
np.save(OUT + '/masks.npy', np.stack(masks))
print('frames', i)
