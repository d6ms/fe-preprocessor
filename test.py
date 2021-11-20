import os

import numpy as np
np.random.seed(1337)

from keras.models import model_from_json  



model = model_from_json(open('my_model.json').read())  
model.load_weights('my_model_weights.h5')

word_to_index = dict()
with open('word2vecNocopy.200d.txt') as f:
    for i, line in enumerate(f):
        values = line.split()
        word_to_index[values[0]] = i
word_to_index['*'] = len(word_to_index)
word_to_index['<unk>'] = len(word_to_index)

def to_sequence(batch):
    sequence = []
    for line in batch:
        l = []
        for word in line:
            if word not in word_to_index:
                index = len(word_to_index) - 1
            else:
                index = word_to_index[word]
            l.append(index)
        sequence.append(l)
    return np.asarray(sequence, dtype='int32')


def iterate_data():
    DATA_PATH = 'data/test'
    for project in os.listdir(DATA_PATH):
        for filename in os.listdir(DATA_PATH + '/' + project):
            lines = []
            with open(DATA_PATH + '/' + project + '/' + filename, mode='r') as f:
                for i, line in enumerate(f.readlines()):
                    line = line.rstrip()
                    if i == 0:
                        correct_index = int(line)
                    else:
                        lines.append(line)
            names = [line.split(' ')[:15] for line in lines]
            names = to_sequence(names)
            distances = np.array([line.split(' ')[15:-1] for line in lines])
            distances = np.expand_dims(distances, axis=2)
            yield names, distances, correct_index


n_methods = 0
accurate, tp, tn, fp, fn = 0, 0, 0, 0, 0
def log_result(last=False):
    accuracy = (accurate / tp) if tp > 0 else None
    precision = (tp / (tp + fp)) if tp + fp > 0 else 0
    recall = (tp / (tp + fn)) if tp + fn > 0 else 0
    f1 = (2 * precision * recall / (precision + recall)) if precision != 0 and recall != 0 else 0
    sentence = '[last] ' if last else ''
    sentence += f'n_methods: {n_methods}, accuracy {accuracy}, precision {precision}, recall {recall}, f1 {f1}'
    print(sentence)

for names, distances, correct_index in iterate_data():
    is_moved = correct_index >= 0
    pred = model.predict([names, distances])
    if np.all(pred[:, 1] < 0.5):
        pred_class = -1
    else:
        pred_class = pred[:, 1].argmax()

    if pred_class >= 0:
        if is_moved:
            tp += 1
            if correct_index == pred_class:
                accurate += 1
        else:
            fp += 1
    else:
        if is_moved:
            fn += 1
        else:
            tn += 1

    n_methods += 1
    if n_methods % 1000 == 0:
        log_result()

log_result(True)
