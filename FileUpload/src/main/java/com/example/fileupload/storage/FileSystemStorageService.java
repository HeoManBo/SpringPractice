package com.example.fileupload.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;

    @Autowired
    public FileSystemStorageService(StorageProperties properties){
        if(properties.getLocation().trim().length() == 0){
            throw new StorageException("파일 저장 폴더가 반드시 있어야 합니다.");
        }

        this.rootLocation = Paths.get(properties.getLocation());
    }

    @Override
    public void init() {
        try{
            Files.createDirectories(rootLocation);
        }
        catch (IOException e){
            throw new StorageException("저장소 초기화 불가능.", e);
        }
    }

    @Override
    public void store(MultipartFile file) {
        try{
            if(file.isEmpty()){
                throw  new StorageException("빈 파일은 저장할 수 없습니다.");
            }
            Path destinationFile = this.rootLocation.resolve(
                    Paths.get(file.getOriginalFilename())).normalize().toAbsolutePath();
            if(!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())){
                //보안 체크
                throw new StorageException("현재 디렉토리 외에 파일을 저장할 수 없습니다.");
            }
            try(InputStream inputStream = file.getInputStream()){
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException exception){
            throw  new StorageException("파일 저장 실패..");
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try{
            return Files.walk(this.rootLocation, 1).filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        }
        catch (IOException e) {
            throw new StorageException("파일 읽기 실패..", e);
        }
    }

    @Override
    public Path load(String fileName) {
        return rootLocation.resolve(fileName);
    }

    @Override
    public Resource loadAsResource(String fileName) {
        try{
            Path file = load(fileName);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            else{
                throw new StorageFileNotFoundException("파일을 읽을 수 없습니다 : " + fileName);
            }
        }catch (MalformedURLException e){
            throw new StorageFileNotFoundException("파일을 읽을 수 없습니다 : " + fileName);
        }
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}
