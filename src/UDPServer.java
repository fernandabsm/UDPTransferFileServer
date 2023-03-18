import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UDPServer {

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String FILES_DIRECTORY = "src/resources";
    private static final String PASSWORD = "123456";
    private final DatagramSocket socket;

    public UDPServer() {
        try {
            socket = new DatagramSocket(1997);
            System.out.println("Servidor iniciado. Aguardando conexões...");
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public void acceptConnections() throws IOException {
        while (true) {
            // aguarda a conexão do cliente
            byte[] synPacketData = new byte[3];
            DatagramPacket synPacket = new DatagramPacket(synPacketData, synPacketData.length);
            socket.receive(synPacket);

            // verifica se é um pacote SYN
            String synPacketPayload = new String(synPacket.getData(), 0, synPacket.getLength());
            if (!synPacketPayload.equals("SYN")) {
                throw new IOException("Solicitação Inválida.");
            }

            // Cria o pacote SYN-ACK
            String synAckPayload = "SYN-ACK";
            byte[] synAckPacketData = synAckPayload.getBytes();
            DatagramPacket synAckPacket = new DatagramPacket(synAckPacketData, synAckPacketData.length, synPacket.getAddress(), synPacket.getPort());

            // Envia o pacote SYN-ACK para o cliente
            socket.send(synAckPacket);

            // aguarda o ack do cliente
            byte[] ackPacketData = new byte[3];
            DatagramPacket ackPacket = new DatagramPacket(ackPacketData, ackPacketData.length);
            socket.receive(ackPacket);

            // verifica se é um pacote ACK
            String ackPacketPayload = new String(ackPacket.getData(), 0, ackPacket.getLength());
            if (!ackPacketPayload.equals("ACK")) {
                throw new IOException("Solicitação Inválida.");
            }

            // obtém a senha do cliente
            byte[] passwordBuffer = new byte[BUFFER_SIZE];
            DatagramPacket passwordPacket = new DatagramPacket(passwordBuffer, passwordBuffer.length);
            socket.receive(passwordPacket);
            String password = new String(passwordPacket.getData(), 0, passwordPacket.getLength());

            if (password.equals(PASSWORD)) {
                System.out.println("Conexão estabelecida. Cliente autorizado.");

                // aguarda a seleção do tipo de operação pelo cliente
                byte[] operationTypeBuffer = new byte[BUFFER_SIZE];
                DatagramPacket operationTypePacket = new DatagramPacket(operationTypeBuffer, operationTypeBuffer.length);
                socket.receive(operationTypePacket);
                String operationType = new String(operationTypePacket.getData(), 0, operationTypePacket.getLength());

                if (operationType.equalsIgnoreCase("upload")) {
                    // recebe o arquivo enviado pelo cliente
                    receberArquivo(socket, synPacket.getAddress(), synPacket.getPort());
                } else if (operationType.equalsIgnoreCase("download")) {
                    // envia a lista de arquivos para o cliente
                    String fileList = listarArquivos();
                    byte[] fileListBytes = fileList.getBytes();
                    DatagramPacket fileListPacket = new DatagramPacket(fileListBytes, fileListBytes.length,
                            synPacket.getAddress(), synPacket.getPort());
                    socket.send(fileListPacket);

                    // aguarda a seleção do arquivo pelo cliente
                    byte[] fileSelectionBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket fileSelectionPacket = new DatagramPacket(fileSelectionBuffer, fileSelectionBuffer.length);
                    socket.receive(fileSelectionPacket);
                    String fileName = new String(fileSelectionPacket.getData(), 0, fileSelectionPacket.getLength());

                    // envia o arquivo selecionado pelo cliente
                    enviarArquivo(fileName, synPacket.getAddress(), synPacket.getPort(), socket);
                } else {
                    System.out.println("Operação inválida: " + operationType);
                }
            } else {
                System.out.println("Conexão recusada. Senha incorreta.");
            }
        }
    }

    private static String listarArquivos() {
        StringBuilder fileList = new StringBuilder();
        try {
            Files.list(Paths.get(FILES_DIRECTORY))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .forEach(fileName -> fileList.append(fileName).append("\n"));
        } catch (IOException e) {
            System.err.println("Erro ao listar arquivos: " + e.getMessage());
        }
        return fileList.toString();
    }

    private static void enviarArquivo(String fileName, InetAddress address, int port, DatagramSocket socket) throws IOException {
        Path filePath = Paths.get(FILES_DIRECTORY, fileName);
        if (Files.exists(filePath)) {
            byte[] fileBytes = Files.readAllBytes(filePath);
            DatagramPacket filePacket = new DatagramPacket(fileBytes, fileBytes.length, address, port);
            socket.send(filePacket);
            System.out.println("Arquivo \"" + fileName + "\" enviado para o cliente.");
        } else {
            System.out.println("Arquivo não encontrado: " + fileName);
        }
    }

    private static void receberArquivo(DatagramSocket socket, InetAddress address, int port) throws IOException {
        // recebe o nome do arquivo enviado pelo cliente
        byte[] fileNameBuffer = new byte[BUFFER_SIZE];
        DatagramPacket fileNamePacket = new DatagramPacket(fileNameBuffer, fileNameBuffer.length);
        socket.receive(fileNamePacket);
        String fileName = new String(fileNamePacket.getData(), 0, fileNamePacket.getLength());

        // recebe os dados do arquivo enviado pelo cliente
        byte[] fileBuffer = new byte[BUFFER_SIZE];
        DatagramPacket filePacket = new DatagramPacket(fileBuffer, fileBuffer.length);
        socket.receive(filePacket);

        // salva o arquivo no servidor
        Path filePath = Paths.get(FILES_DIRECTORY, fileName);
        Files.write(filePath, filePacket.getData());
        System.out.println("Arquivo \"" + fileName + "\" recebido do cliente e salvo no servidor.");
    }

    public static void main(String[] args) throws IOException {
        UDPServer udpServer = new UDPServer();
        udpServer.acceptConnections();
    }
}
