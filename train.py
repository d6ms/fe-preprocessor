import os

import numpy as np
import time
np.random.seed(1337)

from keras.preprocessing.text import Tokenizer
from keras.preprocessing.sequence import pad_sequences
from keras.utils.np_utils import to_categorical
from keras.layers import Input, Embedding, Conv1D, Flatten, Concatenate, Dense
from keras.models import Model

from keras.utils import Sequence
from keras.utils.np_utils import to_categorical
import pandas as pd


MAX_SEQUENCE_LENGTH = 15
EMBEDDING_DIM = 200 # Dimension of word vector

word_to_index = dict()
coefs = []
with open('word2vecNocopy.200d.txt') as f:
    for i, line in enumerate(f):
        values = line.split()
        word_to_index[values[0]] = i
        coefs.append(values[1:])
word_to_index['*'] = len(word_to_index)
coefs.append(['0'] * 200)
word_to_index['<unk>'] = len(word_to_index)
coefs.append(['0'] * 200)
coefs = np.asarray(coefs, dtype='float32')

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

class DataGenerator(Sequence):

    def __init__(self, batch_size):
        self.batch_size = batch_size
        self.gen = self.generator() 
        self.gen.__next__()

    def generator(self):
        with open('data/training/training.txt') as f:
            batch = []
            i = 0
            for line in f:
                if i % self.batch_size == 0:
                    yield batch
                    batch = []
                batch.append(line)
                i += 1

    def __len__(self):
        # 事前に wc -l して調べた行数
        return 389535102 // self.batch_size

    def __getitem__(self, idx):
        try:
            lines = self.gen.__next__()
        except StopIteration:
            self.gen = self.generator()
            self.gen.__next__()
            lines = self.gen.__next__()
        if lines is None:
            self.gen = self.generator()
            self.gen.__next__()
            lines = self.gen.__next__()
        b_names = [line.split(' ')[:15] for line in lines]
        b_names = to_sequence(b_names)
        b_distances = np.array([line.split(' ')[15:-1] for line in lines])
        b_distances = np.expand_dims(b_distances, axis=2)
        b_y = to_categorical(np.array([line.split(' ')[-1].rstrip() for line in lines]))
        return [b_names, b_distances], b_y


# model definition ====================================

l_input = Input(shape=(15,), dtype='int32', name='l_input')
embedded = Embedding(len(word_to_index),
                            EMBEDDING_DIM,
                            input_length=MAX_SEQUENCE_LENGTH,
                            weights=[coefs],
                            trainable=True)(l_input)

l = Conv1D(128, 1, padding = "same", activation='tanh')(embedded)
l = Conv1D(128,1, activation='tanh')(l)
l = Conv1D(128, 1, activation='tanh')(l)
l = Flatten()(l)

r_input = Input(shape=(2, 1), dtype='float32', name='r_input')
r = Conv1D(128, 1, padding = "same", activation='tanh')(r_input)
r = Conv1D(128, 1, activation='tanh')(r)
r = Conv1D(128, 1, activation='tanh')(r)
r = Flatten()(r)

merged = Concatenate(axis=1)([l, r])
d = Dense(128, activation='tanh')(merged)
output = Dense(2, activation='sigmoid')(d)

model = Model(inputs=[l_input, r_input], outputs=output)
model.compile(loss='binary_crossentropy',
              optimizer='Adadelta',
              metrics=['accuracy'])



# training ==============================================

print ("start training:"+time.strftime("%Y/%m/%d  %H:%M:%S"))
batch_size = 1024
generator = DataGenerator(batch_size)
model.fit_generator(generator, epochs=2)

json_string = model.to_json()
open('my_model.json','w').write(json_string)
model.save_weights('my_model_weights.h5')

print ("end time:"+time.strftime("%Y/%m/%d  %H:%M:%S"))
