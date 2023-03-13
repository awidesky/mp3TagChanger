# mp3TagChanger

Change ID3v2 tag of mp3 files to fix [album art bug](https://r2.community.samsung.com/t5/Galaxy-A/All-of-my-songs-in-Samsung-Music-show-the-same-album-art/td-p/11656917) of Samsung music app.


## Command Line usage
```
Usage : java -jar mp3TagChanger.jar [options]
Options : 
	--help	Show this help message

	--artistIndex=<index>	Index of artist name in name of mp3 file
				if artist name comes before song name, value should be 0(default). ex)"The Beatles - Let it be.mp3"
				if song name comes before artist name, value should be 1. ex)"Let it be - The Beatles.mp3"

	--artistDelimiter=<delimiter>	Set delimiter between song name and artist.
					Default is "-", so mp3 file name would be like : "The Beatles - Let it be.mp3"

	--overwrite	Ovewrite existing mp3 tag(title, artist, album)

	--random	Set mp3 tag(title, artist, album) to random meaningless text
```
