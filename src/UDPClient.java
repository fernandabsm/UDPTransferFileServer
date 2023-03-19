
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

public class UDPClient {

    private static final int PACKET_SIZE = 1460; // tamanho máximo de carga útil de um pacote UDP
    private static final int WINDOW_SIZE = 10; // tamanho da janela inicial
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String FILES_DIRECTORY = "src/cliente/resources";
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 1997;
    private final DatagramSocket socket;
    private final InetAddress serverAddress;

    public UDPClient() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName(SERVER_ADDRESS);
            System.out.println("Conectando ao servidor...");
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void createConnection() throws IOException {
            // estabelecendo a conexão
            threeWayHandshake();

            // solicita a senha do cliente
            Scanner scanner = new Scanner(System.in);
            System.out.print("Digite a senha: ");
            String password = scanner.nextLine();

            // envia a senha do cliente
            byte[] passwordBytes = password.getBytes();
            DatagramPacket passwordPacket = new DatagramPacket(passwordBytes, passwordBytes.length, serverAddress, SERVER_PORT);
            socket.send(passwordPacket);

            String operationType;

            byte[] operationTypeBytes;
            do {
                // solicita o tipo de operação que o cliente deseja realizar
                System.out.println("Selecione o tipo de operação:");
                System.out.println("1. Upload");
                System.out.println("2. Download");
                System.out.print("> ");
                operationType = scanner.nextLine();

                // envia o tipo de operação selecionado pelo cliente
                operationTypeBytes = operationType.getBytes();
                DatagramPacket operationTypePacket = new DatagramPacket(operationTypeBytes, operationTypeBytes.length, serverAddress, SERVER_PORT);
                socket.send(operationTypePacket);

                if (operationType.equalsIgnoreCase("upload")) {
                    realizarUpload(socket, serverAddress, scanner);
                } else if (operationType.equalsIgnoreCase("download")) {
                    realizarDownload(socket, serverAddress, scanner);
                } else {
                    System.out.println("Operação inválida.");
                }
            } while (!operationType.equalsIgnoreCase("upload") && !operationType.equalsIgnoreCase("download"));
    }

    private void threeWayHandshake() throws IOException {
        // envia um pacote SYN para estabelecer a conexão com o servidor
        byte[] synPacketData = "SYN".getBytes();
        DatagramPacket synPacket = new DatagramPacket(synPacketData, synPacketData.length, serverAddress, SERVER_PORT);
        socket.send(synPacket);

        // aguarda a resposta do servidor
        byte[] synAckPacketData = new byte[BUFFER_SIZE];
        DatagramPacket synAckPacket = new DatagramPacket(synAckPacketData, synAckPacketData.length);
        socket.receive(synAckPacket);

        // verifica se a resposta do servidor é um pacote SYN-ACK
        String synAckPacketPayload = new String(synAckPacket.getData(), 0, synAckPacket.getLength());
        if (!synAckPacketPayload.equals("SYN-ACK")) {
            throw new IOException("Resposta inválida do servidor: " + synAckPacketPayload);
        }

        // envia um pacote ACK para confirmar a conexão com o servidor
        byte[] ackPacketData = "ACK".getBytes();
        DatagramPacket ackPacket = new DatagramPacket(ackPacketData, ackPacketData.length, serverAddress, SERVER_PORT);
        socket.send(ackPacket);
    }

    private static void realizarUpload(DatagramSocket socket, InetAddress serverAddress, Scanner scanner) throws IOException {
        // solicita o nome do arquivo a ser enviado
        System.out.print("Digite o nome do arquivo a ser enviado: ");
        String fileName = scanner.nextLine();

        // verifica se o arquivo existe
        Path filePath = Paths.get(FILES_DIRECTORY, fileName);
        if (!Files.exists(filePath)) {
            System.out.println("Arquivo não encontrado: " + fileName);
            return;
        }

        // envia o nome do arquivo para o servidor
        byte[] fileNameBytes = fileName.getBytes();
        DatagramPacket fileNamePacket = new DatagramPacket(fileNameBytes, fileNameBytes.length, serverAddress, SERVER_PORT);
        socket.send(fileNamePacket);

        // envia os dados do arquivo para o servidor
        byte[] fileBytes = Files.readAllBytes(filePath);
        DatagramPacket filePacket = new DatagramPacket(fileBytes, fileBytes.length, serverAddress, SERVER_PORT);
        socket.send(filePacket);

        System.out.println("Arquivo \"" + fileName + "\" enviado para o servidor.");
    }

    private static void realizarDownload(DatagramSocket socket, InetAddress serverAddress, Scanner scanner) throws IOException {
        // recebe a lista de arquivos disponíveis no servidor
        byte[] filesListBytes = new byte[BUFFER_SIZE];
        DatagramPacket filesListPacket = new DatagramPacket(filesListBytes, filesListBytes.length);
        socket.receive(filesListPacket);
        String filesList = new String(filesListPacket.getData(), 0, filesListPacket.getLength());
        System.out.println("Arquivos disponíveis no servidor:");
        System.out.println(filesList);

        // solicita o nome do arquivo a ser baixado
        System.out.print("Digite o nome do arquivo a ser baixado: ");
        String fileName = scanner.nextLine();

        // envia o nome do arquivo para o servidor
        byte[] fileNameBytes = fileName.getBytes();
        DatagramPacket fileNamePacket = new DatagramPacket(fileNameBytes, fileNameBytes.length, serverAddress, SERVER_PORT);
        socket.send(fileNamePacket);

        // recebe o tamanho do arquivo selecionado do servidor
        byte[] fileSizeBytes = new byte[4];
        DatagramPacket fileSizePacket = new DatagramPacket(fileSizeBytes, fileSizeBytes.length);
        socket.receive(fileSizePacket);
        int fileSize = ByteBuffer.wrap(fileSizeBytes).getInt();

        // envia confirmação de recebimento do tamanho do arquivo
        DatagramPacket ackPacket = new DatagramPacket(new byte[] { 1 }, 1, serverAddress, SERVER_PORT);
        socket.send(ackPacket);

        // cria buffer para armazenar os dados do arquivo recebidos
        byte[] fileBytes = new byte[fileSize];

        // inicia janela deslizante com tamanho definido pela variável WINDOW_SIZE
        int seqNum = 0;
        int windowBase = 0;
        int lastAckReceived = 0;
        boolean endOfFile = false;

        // recebe os pacotes do servidor enquanto a janela deslizante não chega ao final do arquivo
        while (!endOfFile) {
            // envia acks para os pacotes já recebidos e processados pela janela deslizante
            while (seqNum <= lastAckReceived + WINDOW_SIZE && seqNum < fileSize / PACKET_SIZE) {
                ackPacket = new DatagramPacket(intToBytes(seqNum), 4, serverAddress, SERVER_PORT);
                socket.send(ackPacket);
                seqNum++;
            }

            // recebe pacote do servidor
            byte[] packetBytes = new byte[PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(packetBytes, packetBytes.length);
            socket.receive(packet);

            // extrai informações do pacote (número de sequência, dados)
            int packetSeqNum = ByteBuffer.wrap(packetBytes, 0, 4).getInt();
            byte[] packetData = Arrays.copyOfRange(packetBytes, 4, packet.getLength());

            // atualiza janela deslizante
            if (packetSeqNum >= windowBase && packetSeqNum < windowBase + WINDOW_SIZE) {
                int dataOffset = packetSeqNum * PACKET_SIZE;
                System.arraycopy(packetData, 0, fileBytes, dataOffset, packetData.length);
                if (packetSeqNum == windowBase) {
                    int numPacketsProcessed = 1;
                    while (windowBase < fileSize / PACKET_SIZE && numPacketsProcessed < WINDOW_SIZE) {
                        if (windowBase + numPacketsProcessed == seqNum) {
                            break;
                        }
                        int nextPacketOffset = (windowBase + numPacketsProcessed) * PACKET_SIZE;
                        if (fileBytes[nextPacketOffset] != 0) {
                            numPacketsProcessed++;
                        } else {
                            break;
                        }
                    }
                    lastAckReceived = windowBase + numPacketsProcessed - 1;
                    windowBase += numPacketsProcessed;
                }
                if (windowBase >= fileSize / PACKET_SIZE) {
                    endOfFile = true;
                }
            }

            // envia ack para o último pacote processado pela janela deslizante
            ackPacket = new DatagramPacket(intToBytes(lastAckReceived), 4, serverAddress, SERVER_PORT);
            socket.send(ackPacket);
        }

        // salva o arquivo no cliente
        Path filePath = Paths.get(FILES_DIRECTORY, fileName);
        Files.write(filePath, fileBytes);

        System.out.println("Arquivo \"" + fileName + "\" baixado do servidor e salvo no cliente.");
    }

    private static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public static void main(String[] args) throws IOException {
        UDPClient udpClient = new UDPClient();
        udpClient.createConnection();
    }
}