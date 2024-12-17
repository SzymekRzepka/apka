import scipy.io
import json

# Ścieżki do plików .mat
labels_file = "C:/Users/User/AndroidStudioProjects/flowers/dataset/imagelabels.mat"
splits_file = "C:/Users/User/AndroidStudioProjects/flowers/dataset/setid.mat"

# Wczytaj dane z plików .mat
labels_data = scipy.io.loadmat(labels_file)
splits_data = scipy.io.loadmat(splits_file)

# Wyciągnij etykiety i podziały
image_labels = labels_data['labels'][0]
train_ids = splits_data['trnid'][0]
val_ids = splits_data['valid'][0]
test_ids = splits_data['tstid'][0]

# Mapowanie obrazów na klasy
label_map = {f"image_{i+1:05d}.jpg": int(label) for i, label in enumerate(image_labels)}

# Podziały na zbiory
splits = {
    "train": [f"image_{i:05d}.jpg" for i in train_ids],
    "validation": [f"image_{i:05d}.jpg" for i in val_ids],
    "test": [f"image_{i:05d}.jpg" for i in test_ids],
}

# Zapisz do JSON
output_file = "C:/Users/User/AndroidStudioProjects/flowers/dataset/flower_labels.json"
with open(output_file, "w") as json_file:
    json.dump({"label_map": label_map, "splits": splits}, json_file)

print(f"Plik {output_file} został utworzony.")
